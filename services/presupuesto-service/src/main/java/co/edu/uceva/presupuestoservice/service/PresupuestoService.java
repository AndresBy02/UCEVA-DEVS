package co.edu.uceva.presupuestoservice.service;

import co.edu.uceva.presupuestoservice.client.CalculoMotorClient;
import co.edu.uceva.presupuestoservice.client.InsumoCatalogoClient;
import co.edu.uceva.presupuestoservice.domain.CodigoDuplicadoException;
import co.edu.uceva.presupuestoservice.domain.PresupuestoAprobadoException;
import co.edu.uceva.presupuestoservice.domain.RecursoNoEncontradoException;
import co.edu.uceva.presupuestoservice.dto.CalculoDtos.CalculoPresupuestoRequest;
import co.edu.uceva.presupuestoservice.dto.CalculoDtos.CalculoPresupuestoResponse;
import co.edu.uceva.presupuestoservice.dto.CalculoDtos.InsumosCriticosRequest;
import co.edu.uceva.presupuestoservice.dto.CalculoDtos.InsumosCriticosResponse;
import co.edu.uceva.presupuestoservice.dto.CalculoDtos.SimulacionSensibilidadRequest;
import co.edu.uceva.presupuestoservice.dto.CalculoDtos.SimulacionSensibilidadResponse;
import co.edu.uceva.presupuestoservice.dto.CapituloCreateRequest;
import co.edu.uceva.presupuestoservice.dto.ComparacionEscenariosResponse;
import co.edu.uceva.presupuestoservice.dto.ComparacionEscenariosResponse.ComparacionCapitulo;
import co.edu.uceva.presupuestoservice.dto.ComparacionEscenariosResponse.ComparacionTotal;
import co.edu.uceva.presupuestoservice.dto.ComparacionEscenariosResponse.EscenarioResumen;
import co.edu.uceva.presupuestoservice.dto.DuplicarEscenarioRequest;
import co.edu.uceva.presupuestoservice.dto.InsumoCatalogoDto;
import co.edu.uceva.presupuestoservice.dto.ItemCreateRequest;
import co.edu.uceva.presupuestoservice.dto.ItemUpdateRequest;
import co.edu.uceva.presupuestoservice.dto.PresupuestoDetalleResponse;
import co.edu.uceva.presupuestoservice.dto.PresupuestoDetalleResponse.CapituloDetalleResponse;
import co.edu.uceva.presupuestoservice.dto.PresupuestoDetalleResponse.ItemDetalleResponse;
import co.edu.uceva.presupuestoservice.dto.PresupuestoRequest;
import co.edu.uceva.presupuestoservice.dto.PresupuestoResumenResponse;
import co.edu.uceva.presupuestoservice.dto.PresupuestoUpdateRequest;
import co.edu.uceva.presupuestoservice.dto.PrevisualizacionPreciosResponse;
import co.edu.uceva.presupuestoservice.dto.PrevisualizacionPreciosResponse.CambioPrecioItem;
import co.edu.uceva.presupuestoservice.model.Capitulo;
import co.edu.uceva.presupuestoservice.model.EstadoPresupuesto;
import co.edu.uceva.presupuestoservice.model.ItemPresupuesto;
import co.edu.uceva.presupuestoservice.model.Presupuesto;
import co.edu.uceva.presupuestoservice.repository.PresupuestoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PresupuestoService {

    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final BigDecimal CERO_MONEDA = new BigDecimal("0.00");

    private final PresupuestoRepository presupuestoRepository;
    private final InsumoCatalogoClient insumoCatalogoClient;
    private final CalculoMotorClient calculoMotorClient;

    public PresupuestoService(
            PresupuestoRepository presupuestoRepository,
            InsumoCatalogoClient insumoCatalogoClient,
            CalculoMotorClient calculoMotorClient) {
        this.presupuestoRepository = presupuestoRepository;
        this.insumoCatalogoClient = insumoCatalogoClient;
        this.calculoMotorClient = calculoMotorClient;
    }

    @Transactional
    public PresupuestoDetalleResponse crearPresupuesto(PresupuestoRequest request, String usuarioSub) {
        String codigo = request.codigo().trim();
        if (presupuestoRepository.existsByCodigo(codigo)) {
            throw new CodigoDuplicadoException(codigo);
        }

        Presupuesto p = new Presupuesto();
        p.setCodigo(codigo);
        p.setNombre(request.nombre().trim());
        p.setCliente(request.cliente().trim());
        p.setEstado(EstadoPresupuesto.BORRADOR);
        p.setPorcentajeAdministracion(normalizarPorcentaje(request.porcentajeAdministracion(), "8.0000"));
        p.setPorcentajeImprevistos(normalizarPorcentaje(request.porcentajeImprevistos(), "3.0000"));
        p.setPorcentajeUtilidad(normalizarPorcentaje(request.porcentajeUtilidad(), "5.0000"));
        p.setCostoFijo(normalizarMoneda(request.costoFijo(), "0.00"));
        p.setPrecioVentaUnitario(normalizarMonedaNullable(request.precioVentaUnitario()));
        p.setCostoVariableUnitario(normalizarMonedaNullable(request.costoVariableUnitario()));
        p.setCreadoPor(usuarioSub);
        p.setFechaCreacion(OffsetDateTime.now(ZoneOffset.UTC));

        if (request.capitulos() != null) {
            Map<UUID, InsumoCatalogoDto> cacheInsumos = new HashMap<>();
            int capOrden = 1;
            for (var capReq : request.capitulos()) {
                Capitulo cap = new Capitulo();
                cap.setPresupuesto(p);
                cap.setOrden(capOrden++);
                cap.setCodigo(capReq.codigo().trim());
                cap.setNombre(capReq.nombre().trim());
                if (capReq.items() != null) {
                    int itemOrden = 1;
                    for (var itemReq : capReq.items()) {
                        ItemPresupuesto item = construirItemDesdeCatalogo(cap, itemOrden++, itemReq, cacheInsumos);
                        cap.getItems().add(item);
                    }
                }
                p.getCapitulos().add(cap);
            }
        }

        Presupuesto guardado = presupuestoRepository.save(p);
        return construirDetalleCalculado(guardado);
    }

    @Transactional(readOnly = true)
    public List<PresupuestoResumenResponse> listarPresupuestos() {
        return presupuestoRepository.findAllByOrderByFechaCreacionDesc().stream()
                .map(PresupuestoService::aResumenResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PresupuestoResumenResponse> listarPorInsumo(UUID insumoId) {
        return presupuestoRepository.findDistinctByInsumoId(insumoId).stream()
                .map(PresupuestoService::aResumenResponse)
                .toList();
    }

    private static PresupuestoResumenResponse aResumenResponse(Presupuesto p) {
        int totalItems = p.getCapitulos().stream()
                .mapToInt(c -> c.getItems().size())
                .sum();
        return new PresupuestoResumenResponse(
                p.getId(),
                p.getCodigo(),
                p.getNombre(),
                p.getCliente(),
                p.getEstado(),
                p.getPresupuestoBaseId(),
                p.getPorcentajeAdministracion(),
                p.getPorcentajeImprevistos(),
                p.getPorcentajeUtilidad(),
                p.getCreadoPor(),
                p.getFechaCreacion(),
                p.getAprobadoPor(),
                p.getFechaAprobacion(),
                p.getCapitulos().size(),
                totalItems
        );
    }

    @Transactional(readOnly = true)
    public PresupuestoDetalleResponse obtenerDetalle(UUID id) {
        return construirDetalleCalculado(buscarPorId(id));
    }

    @Transactional
    public PresupuestoDetalleResponse actualizarPresupuesto(UUID id, PresupuestoUpdateRequest request) {
        Presupuesto p = buscarEditable(id);
        p.setNombre(request.nombre().trim());
        p.setCliente(request.cliente().trim());
        if (request.porcentajeAdministracion() != null) {
            p.setPorcentajeAdministracion(normalizarPorcentaje(request.porcentajeAdministracion(), "8.0000"));
        }
        if (request.porcentajeImprevistos() != null) {
            p.setPorcentajeImprevistos(normalizarPorcentaje(request.porcentajeImprevistos(), "3.0000"));
        }
        if (request.porcentajeUtilidad() != null) {
            p.setPorcentajeUtilidad(normalizarPorcentaje(request.porcentajeUtilidad(), "5.0000"));
        }
        if (request.costoFijo() != null) {
            p.setCostoFijo(normalizarMoneda(request.costoFijo(), "0.00"));
        }
        p.setPrecioVentaUnitario(normalizarMonedaNullable(request.precioVentaUnitario()));
        p.setCostoVariableUnitario(normalizarMonedaNullable(request.costoVariableUnitario()));

        return construirDetalleCalculado(presupuestoRepository.save(p));
    }

    @Transactional
    public PresupuestoDetalleResponse agregarCapitulo(UUID presupuestoId, CapituloCreateRequest request) {
        Presupuesto p = buscarEditable(presupuestoId);
        int siguienteOrden = p.getCapitulos().stream()
                .mapToInt(Capitulo::getOrden)
                .max()
                .orElse(0) + 1;

        Capitulo cap = new Capitulo();
        cap.setPresupuesto(p);
        cap.setOrden(siguienteOrden);
        cap.setCodigo(request.codigo().trim());
        cap.setNombre(request.nombre().trim());
        p.getCapitulos().add(cap);

        return construirDetalleCalculado(presupuestoRepository.save(p));
    }

    @Transactional
    public PresupuestoDetalleResponse actualizarCapitulo(
            UUID presupuestoId, UUID capituloId, CapituloCreateRequest request) {
        Presupuesto p = buscarEditable(presupuestoId);
        Capitulo cap = buscarCapitulo(p, capituloId);
        cap.setCodigo(request.codigo().trim());
        cap.setNombre(request.nombre().trim());
        return construirDetalleCalculado(presupuestoRepository.save(p));
    }

    @Transactional
    public PresupuestoDetalleResponse eliminarCapitulo(UUID presupuestoId, UUID capituloId) {
        Presupuesto p = buscarEditable(presupuestoId);
        Capitulo cap = buscarCapitulo(p, capituloId);
        p.getCapitulos().remove(cap);
        return construirDetalleCalculado(presupuestoRepository.save(p));
    }

    @Transactional
    public PresupuestoDetalleResponse agregarItem(UUID presupuestoId, UUID capituloId, ItemCreateRequest request) {
        Presupuesto p = buscarEditable(presupuestoId);
        Capitulo cap = buscarCapitulo(p, capituloId);

        int siguienteOrden = cap.getItems().stream()
                .mapToInt(ItemPresupuesto::getOrden)
                .max()
                .orElse(0) + 1;

        ItemPresupuesto item = construirItemDesdeCatalogo(cap, siguienteOrden, request, new HashMap<>());
        cap.getItems().add(item);

        return construirDetalleCalculado(presupuestoRepository.save(p));
    }

    @Transactional
    public PresupuestoDetalleResponse actualizarItem(
            UUID presupuestoId, UUID capituloId, UUID itemId, ItemUpdateRequest request) {
        Presupuesto p = buscarEditable(presupuestoId);
        Capitulo cap = buscarCapitulo(p, capituloId);
        ItemPresupuesto item = buscarItem(cap, itemId);

        item.setCantidad(normalizarEscala3(request.cantidad()));
        if (request.precioUnitarioCopia() != null) {
            item.setPrecioUnitarioCopia(normalizarEscala3(request.precioUnitarioCopia()));
        }

        return construirDetalleCalculado(presupuestoRepository.save(p));
    }

    @Transactional
    public PresupuestoDetalleResponse eliminarItem(UUID presupuestoId, UUID capituloId, UUID itemId) {
        Presupuesto p = buscarEditable(presupuestoId);
        Capitulo cap = buscarCapitulo(p, capituloId);
        ItemPresupuesto item = buscarItem(cap, itemId);
        cap.getItems().remove(item);
        return construirDetalleCalculado(presupuestoRepository.save(p));
    }

    @Transactional(readOnly = true)
    public PrevisualizacionPreciosResponse previsualizarActualizacionPrecios(UUID presupuestoId) {
        Presupuesto p = buscarEditable(presupuestoId);
        Map<UUID, InsumoCatalogoDto> cache = new HashMap<>();
        List<CambioPrecioItem> cambios = new ArrayList<>();
        int totalRevisados = 0;

        for (Capitulo cap : p.getCapitulos()) {
            for (ItemPresupuesto item : cap.getItems()) {
                totalRevisados++;
                InsumoCatalogoDto actual = cache.computeIfAbsent(
                        item.getInsumoId(), insumoCatalogoClient::obtenerInsumo);
                BigDecimal precioVigente = normalizarEscala3(actual.precioUnitario());
                BigDecimal precioPresupuesto = normalizarEscala3(item.getPrecioUnitarioCopia());

                if (precioVigente.compareTo(precioPresupuesto) != 0) {
                    cambios.add(new CambioPrecioItem(
                            item.getId(),
                            cap.getCodigo(),
                            item.getInsumoId(),
                            item.getCodigoInsumo(),
                            item.getDescripcion(),
                            item.getCantidad(),
                            precioPresupuesto,
                            precioVigente,
                            precioVigente.subtract(precioPresupuesto)
                    ));
                }
            }
        }

        return new PrevisualizacionPreciosResponse(
                p.getId(),
                p.getCodigo(),
                totalRevisados,
                cambios.size(),
                cambios
        );
    }

    @Transactional
    public PresupuestoDetalleResponse aplicarActualizacionPrecios(UUID presupuestoId) {
        Presupuesto p = buscarEditable(presupuestoId);
        Map<UUID, InsumoCatalogoDto> cache = new HashMap<>();

        for (Capitulo cap : p.getCapitulos()) {
            for (ItemPresupuesto item : cap.getItems()) {
                InsumoCatalogoDto actual = cache.computeIfAbsent(
                        item.getInsumoId(), insumoCatalogoClient::obtenerInsumo);
                BigDecimal precioVigente = normalizarEscala3(actual.precioUnitario());
                if (precioVigente.compareTo(item.getPrecioUnitarioCopia()) != 0) {
                    item.setPrecioUnitarioCopia(precioVigente);
                }
                item.setDescripcion(actual.descripcion());
                item.setUnidadMedida(actual.unidadMedida());
            }
        }

        return construirDetalleCalculado(presupuestoRepository.save(p));
    }

    @Transactional
    public PresupuestoDetalleResponse duplicarComoEscenario(
            UUID presupuestoBaseId, DuplicarEscenarioRequest request, String usuarioSub) {
        Presupuesto base = buscarPorId(presupuestoBaseId);
        String nuevoCodigo = request.codigo().trim();
        if (presupuestoRepository.existsByCodigo(nuevoCodigo)) {
            throw new CodigoDuplicadoException(nuevoCodigo);
        }

        Presupuesto copia = new Presupuesto();
        copia.setCodigo(nuevoCodigo);
        copia.setNombre(request.nombre().trim());
        copia.setCliente(request.cliente() != null && !request.cliente().isBlank()
                ? request.cliente().trim()
                : base.getCliente());
        copia.setEstado(EstadoPresupuesto.BORRADOR);
        copia.setPresupuestoBaseId(base.getId());
        copia.setPorcentajeAdministracion(base.getPorcentajeAdministracion());
        copia.setPorcentajeImprevistos(base.getPorcentajeImprevistos());
        copia.setPorcentajeUtilidad(base.getPorcentajeUtilidad());
        copia.setCostoFijo(base.getCostoFijo());
        copia.setPrecioVentaUnitario(base.getPrecioVentaUnitario());
        copia.setCostoVariableUnitario(base.getCostoVariableUnitario());
        copia.setCreadoPor(usuarioSub);
        copia.setFechaCreacion(OffsetDateTime.now(ZoneOffset.UTC));

        for (Capitulo capBase : base.getCapitulos()) {
            Capitulo capCopia = new Capitulo();
            capCopia.setPresupuesto(copia);
            capCopia.setOrden(capBase.getOrden());
            capCopia.setCodigo(capBase.getCodigo());
            capCopia.setNombre(capBase.getNombre());

            for (ItemPresupuesto itemBase : capBase.getItems()) {
                ItemPresupuesto itemCopia = new ItemPresupuesto();
                itemCopia.setCapitulo(capCopia);
                itemCopia.setOrden(itemBase.getOrden());
                itemCopia.setInsumoId(itemBase.getInsumoId());
                itemCopia.setCodigoInsumo(itemBase.getCodigoInsumo());
                itemCopia.setDescripcion(itemBase.getDescripcion());
                itemCopia.setUnidadMedida(itemBase.getUnidadMedida());
                itemCopia.setCantidad(itemBase.getCantidad());
                itemCopia.setPrecioUnitarioCopia(itemBase.getPrecioUnitarioCopia());
                capCopia.getItems().add(itemCopia);
            }
            copia.getCapitulos().add(capCopia);
        }

        return construirDetalleCalculado(presupuestoRepository.save(copia));
    }

    @Transactional(readOnly = true)
    public ComparacionEscenariosResponse compararEscenarios(UUID idA, UUID idB) {
        PresupuestoDetalleResponse a = obtenerDetalle(idA);
        PresupuestoDetalleResponse b = obtenerDetalle(idB);

        Map<String, CapituloDetalleResponse> mapA = new LinkedHashMap<>();
        for (var cap : a.capitulos()) {
            mapA.put(cap.codigo(), cap);
        }
        Map<String, CapituloDetalleResponse> mapB = new LinkedHashMap<>();
        for (var cap : b.capitulos()) {
            mapB.put(cap.codigo(), cap);
        }

        Set<String> codigosOrdenados = new LinkedHashSet<>(mapA.keySet());
        codigosOrdenados.addAll(mapB.keySet());

        List<ComparacionCapitulo> capsComparados = new ArrayList<>();
        for (String cod : codigosOrdenados) {
            var capA = mapA.get(cod);
            var capB = mapB.get(cod);
            BigDecimal subA = capA != null ? capA.subtotalCapitulo() : CERO_MONEDA;
            BigDecimal subB = capB != null ? capB.subtotalCapitulo() : CERO_MONEDA;
            BigDecimal difAbs = subB.subtract(subA).setScale(2, RoundingMode.HALF_UP);
            BigDecimal difPct = calcularPorcentajeDiferencia(subA, difAbs);
            String nombre = capA != null ? capA.nombre() : capB.nombre();

            capsComparados.add(new ComparacionCapitulo(cod, nombre, subA, subB, difAbs, difPct));
        }

        BigDecimal difCd = b.costoDirecto().subtract(a.costoDirecto()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal difPv = b.precioVenta().subtract(a.precioVenta()).setScale(2, RoundingMode.HALF_UP);

        return new ComparacionEscenariosResponse(
                new EscenarioResumen(a.id(), a.codigo(), a.nombre(), a.costoDirecto(),
                        a.valorAdministracion(), a.valorImprevistos(), a.valorUtilidad(), a.precioVenta()),
                new EscenarioResumen(b.id(), b.codigo(), b.nombre(), b.costoDirecto(),
                        b.valorAdministracion(), b.valorImprevistos(), b.valorUtilidad(), b.precioVenta()),
                capsComparados,
                new ComparacionTotal(
                        difCd,
                        calcularPorcentajeDiferencia(a.costoDirecto(), difCd),
                        difPv,
                        calcularPorcentajeDiferencia(a.precioVenta(), difPv)
                )
        );
    }

    @Transactional(readOnly = true)
    public SimulacionSensibilidadResponse simularSensibilidad(
            UUID presupuestoId, String codigoInsumo, BigDecimal porcentajeVariacion) {
        Presupuesto p = buscarPorId(presupuestoId);
        return calculoMotorClient.simularSensibilidad(
                new SimulacionSensibilidadRequest(codigoInsumo, porcentajeVariacion, aCalculoRequest(p)));
    }

    @Transactional(readOnly = true)
    public InsumosCriticosResponse obtenerInsumosCriticos(
            UUID presupuestoId, BigDecimal porcentajeReferencia) {
        Presupuesto p = buscarPorId(presupuestoId);
        return calculoMotorClient.identificarInsumosCriticos(
                new InsumosCriticosRequest(porcentajeReferencia, aCalculoRequest(p)));
    }

    @Transactional
    public PresupuestoDetalleResponse aprobarPresupuesto(UUID id, String revisorSub) {
        Presupuesto p = buscarEditable(id);
        p.setEstado(EstadoPresupuesto.APROBADO);
        p.setAprobadoPor(revisorSub);
        p.setFechaAprobacion(OffsetDateTime.now(ZoneOffset.UTC));
        return construirDetalleCalculado(presupuestoRepository.save(p));
    }

    private Presupuesto buscarPorId(UUID id) {
        return presupuestoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Presupuesto no encontrado: " + id));
    }

    private Presupuesto buscarEditable(UUID id) {
        Presupuesto p = buscarPorId(id);
        if (p.getEstado() == EstadoPresupuesto.APROBADO) {
            throw new PresupuestoAprobadoException(
                    "El presupuesto " + p.getCodigo() + " ya está APROBADO y no permite modificaciones.");
        }
        return p;
    }

    private static Capitulo buscarCapitulo(Presupuesto p, UUID capituloId) {
        return p.getCapitulos().stream()
                .filter(c -> c.getId().equals(capituloId))
                .findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("Capítulo no encontrado: " + capituloId));
    }

    private static ItemPresupuesto buscarItem(Capitulo cap, UUID itemId) {
        return cap.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("Ítem no encontrado: " + itemId));
    }

    private ItemPresupuesto construirItemDesdeCatalogo(
            Capitulo cap, int orden, ItemCreateRequest req, Map<UUID, InsumoCatalogoDto> cache) {
        InsumoCatalogoDto insumo = cache.computeIfAbsent(req.insumoId(), insumoCatalogoClient::obtenerInsumo);
        ItemPresupuesto item = new ItemPresupuesto();
        item.setCapitulo(cap);
        item.setOrden(orden);
        item.setInsumoId(insumo.id());
        item.setCodigoInsumo(insumo.codigo());
        item.setDescripcion(insumo.descripcion());
        item.setUnidadMedida(insumo.unidadMedida());
        item.setCantidad(normalizarEscala3(req.cantidad()));
        item.setPrecioUnitarioCopia(normalizarEscala3(insumo.precioUnitario()));
        return item;
    }

    private PresupuestoDetalleResponse construirDetalleCalculado(Presupuesto p) {
        CalculoPresupuestoResponse calc = calculoMotorClient.calcularPresupuesto(aCalculoRequest(p));

        Map<String, CalculoPresupuestoResponse.CapituloCalculado> capCalcMap = new HashMap<>();
        for (var c : calc.capitulos()) {
            capCalcMap.put(c.capituloId(), c);
        }

        List<CapituloDetalleResponse> capsOut = new ArrayList<>();
        for (Capitulo cap : p.getCapitulos()) {
            var capCalc = capCalcMap.get(cap.getId().toString());
            Map<String, CalculoPresupuestoResponse.ItemCalculado> itemCalcMap = new HashMap<>();
            if (capCalc != null) {
                for (var ic : capCalc.items()) {
                    itemCalcMap.put(ic.itemId(), ic);
                }
            }

            List<ItemDetalleResponse> itemsOut = new ArrayList<>();
            for (ItemPresupuesto item : cap.getItems()) {
                var ic = itemCalcMap.get(item.getId().toString());
                itemsOut.add(new ItemDetalleResponse(
                        item.getId(),
                        item.getOrden(),
                        item.getInsumoId(),
                        item.getCodigoInsumo(),
                        item.getDescripcion(),
                        item.getUnidadMedida(),
                        item.getCantidad(),
                        item.getPrecioUnitarioCopia(),
                        ic != null ? ic.valorParcial() : CERO_MONEDA
                ));
            }

            capsOut.add(new CapituloDetalleResponse(
                    cap.getId(),
                    cap.getOrden(),
                    cap.getCodigo(),
                    cap.getNombre(),
                    capCalc != null ? capCalc.subtotalCapitulo() : CERO_MONEDA,
                    itemsOut
            ));
        }

        return new PresupuestoDetalleResponse(
                p.getId(),
                p.getCodigo(),
                p.getNombre(),
                p.getCliente(),
                p.getEstado(),
                p.getPresupuestoBaseId(),
                p.getCreadoPor(),
                p.getFechaCreacion(),
                p.getAprobadoPor(),
                p.getFechaAprobacion(),
                p.getPorcentajeAdministracion(),
                p.getPorcentajeImprevistos(),
                p.getPorcentajeUtilidad(),
                p.getCostoFijo(),
                p.getPrecioVentaUnitario(),
                p.getCostoVariableUnitario(),
                capsOut,
                calc.costoDirecto(),
                calc.valorAdministracion(),
                calc.valorImprevistos(),
                calc.valorUtilidad(),
                calc.precioVenta(),
                calc.margenSobreCostoPorcentaje(),
                calc.rotuloMargenSobreCosto(),
                calc.margenSobrePrecioVentaPorcentaje(),
                calc.rotuloMargenSobrePrecioVenta(),
                calc.puntoEquilibrio()
        );
    }

    private static CalculoPresupuestoRequest aCalculoRequest(Presupuesto p) {
        List<CalculoPresupuestoRequest.CapituloRequest> caps = new ArrayList<>();
        for (Capitulo cap : p.getCapitulos()) {
            List<CalculoPresupuestoRequest.ItemRequest> items = new ArrayList<>();
            for (ItemPresupuesto item : cap.getItems()) {
                items.add(new CalculoPresupuestoRequest.ItemRequest(
                        item.getId().toString(),
                        item.getCodigoInsumo(),
                        item.getDescripcion(),
                        item.getUnidadMedida(),
                        item.getCantidad(),
                        item.getPrecioUnitarioCopia()
                ));
            }
            caps.add(new CalculoPresupuestoRequest.CapituloRequest(
                    cap.getId().toString(), cap.getNombre(), items));
        }

        return new CalculoPresupuestoRequest(
                p.getPorcentajeAdministracion(),
                p.getPorcentajeImprevistos(),
                p.getPorcentajeUtilidad(),
                p.getCostoFijo(),
                p.getPrecioVentaUnitario(),
                p.getCostoVariableUnitario(),
                caps
        );
    }

    private static BigDecimal calcularPorcentajeDiferencia(BigDecimal base, BigDecimal diferencia) {
        if (base == null || base.compareTo(BigDecimal.ZERO) == 0) {
            return CERO_MONEDA;
        }
        return diferencia.multiply(CIEN).divide(base, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalizarEscala3(BigDecimal valor) {
        return new BigDecimal(valor.toPlainString()).setScale(3, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal normalizarPorcentaje(BigDecimal valor, String valorPorDefecto) {
        if (valor == null) {
            return new BigDecimal(valorPorDefecto);
        }
        return new BigDecimal(valor.toPlainString()).setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalizarMoneda(BigDecimal valor, String valorPorDefecto) {
        if (valor == null) {
            return new BigDecimal(valorPorDefecto);
        }
        return new BigDecimal(valor.toPlainString()).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalizarMonedaNullable(BigDecimal valor) {
        if (valor == null) {
            return null;
        }
        return new BigDecimal(valor.toPlainString()).setScale(2, RoundingMode.HALF_UP);
    }
}
