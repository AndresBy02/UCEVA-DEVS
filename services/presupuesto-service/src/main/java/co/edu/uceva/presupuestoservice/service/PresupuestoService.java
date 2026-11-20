package co.edu.uceva.presupuestoservice.service;

import co.edu.uceva.presupuestoservice.client.CalculoMotorClient;
import co.edu.uceva.presupuestoservice.client.InsumoCatalogoClient;
import co.edu.uceva.presupuestoservice.domain.CodigoDuplicadoException;
import co.edu.uceva.presupuestoservice.domain.EstadoPresupuestoInvalidoException;
import co.edu.uceva.presupuestoservice.domain.PresupuestoAprobadoException;
import co.edu.uceva.presupuestoservice.domain.PresupuestoNoEditableException;
import co.edu.uceva.presupuestoservice.domain.RecursoNoEncontradoException;
import co.edu.uceva.presupuestoservice.dto.CalculoDtos.CalculoPresupuestoRequest;
import co.edu.uceva.presupuestoservice.dto.CalculoDtos.CalculoPresupuestoResponse;
import co.edu.uceva.presupuestoservice.dto.CalculoDtos.DesembolsosRequest;
import co.edu.uceva.presupuestoservice.dto.CalculoDtos.DesembolsosResponse;
import co.edu.uceva.presupuestoservice.dto.CalculoDtos.InsumosCriticosRequest;
import co.edu.uceva.presupuestoservice.dto.CalculoDtos.InsumosCriticosResponse;
import co.edu.uceva.presupuestoservice.dto.CalculoDtos.SimulacionSensibilidadRequest;
import co.edu.uceva.presupuestoservice.dto.CalculoDtos.SimulacionSensibilidadResponse;
import co.edu.uceva.presupuestoservice.dto.CapituloCreateRequest;
import co.edu.uceva.presupuestoservice.dto.ComparacionEscenariosResponse;
import co.edu.uceva.presupuestoservice.dto.ComparacionEscenariosResponse.ComparacionCapitulo;
import co.edu.uceva.presupuestoservice.dto.ComparacionEscenariosResponse.ComparacionTotal;
import co.edu.uceva.presupuestoservice.dto.ComparacionEscenariosResponse.EscenarioResumen;
import co.edu.uceva.presupuestoservice.dto.ComparacionVersionesResponse;
import co.edu.uceva.presupuestoservice.dto.ComparacionVersionesResponse.ItemVersionCambio;
import co.edu.uceva.presupuestoservice.dto.ComparacionVersionesResponse.ItemVersionModificado;
import co.edu.uceva.presupuestoservice.dto.CrearVersionRequest;
import co.edu.uceva.presupuestoservice.dto.DesembolsoConfigRequest;
import co.edu.uceva.presupuestoservice.dto.DuplicarEscenarioRequest;
import co.edu.uceva.presupuestoservice.dto.EnviarRevisionRequest;
import co.edu.uceva.presupuestoservice.dto.EnviarRevisionResponse;
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
import co.edu.uceva.presupuestoservice.dto.VersionPresupuestoDetalleResponse;
import co.edu.uceva.presupuestoservice.dto.VersionPresupuestoResumenResponse;
import co.edu.uceva.presupuestoservice.model.Capitulo;
import co.edu.uceva.presupuestoservice.model.DesembolsoCapitulo;
import co.edu.uceva.presupuestoservice.model.EstadoPresupuesto;
import co.edu.uceva.presupuestoservice.model.ItemPresupuesto;
import co.edu.uceva.presupuestoservice.model.Presupuesto;
import co.edu.uceva.presupuestoservice.model.VersionPresupuesto;
import co.edu.uceva.presupuestoservice.repository.DesembolsoCapituloRepository;
import co.edu.uceva.presupuestoservice.repository.PresupuestoRepository;
import co.edu.uceva.presupuestoservice.repository.VersionPresupuestoRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(PresupuestoService.class);
    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final BigDecimal CERO_MONEDA = new BigDecimal("0.00");
    private static final String DESTINATARIO_REVISION_POR_DEFECTO = "revisor";

    private final PresupuestoRepository presupuestoRepository;
    private final VersionPresupuestoRepository versionPresupuestoRepository;
    private final DesembolsoCapituloRepository desembolsoCapituloRepository;
    private final InsumoCatalogoClient insumoCatalogoClient;
    private final CalculoMotorClient calculoMotorClient;
    private final ObjectMapper objectMapper;

    public PresupuestoService(
            PresupuestoRepository presupuestoRepository,
            VersionPresupuestoRepository versionPresupuestoRepository,
            DesembolsoCapituloRepository desembolsoCapituloRepository,
            InsumoCatalogoClient insumoCatalogoClient,
            CalculoMotorClient calculoMotorClient,
            ObjectMapper objectMapper) {
        this.presupuestoRepository = presupuestoRepository;
        this.versionPresupuestoRepository = versionPresupuestoRepository;
        this.desembolsoCapituloRepository = desembolsoCapituloRepository;
        this.insumoCatalogoClient = insumoCatalogoClient;
        this.calculoMotorClient = calculoMotorClient;
        this.objectMapper = objectMapper;
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
                .map(this::aResumenResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PresupuestoResumenResponse> listarEnRevision() {
        return presupuestoRepository.findByEstadoOrderByFechaEnvioRevisionDesc(EstadoPresupuesto.EN_REVISION).stream()
                .map(this::aResumenResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PresupuestoResumenResponse> listarPorInsumo(UUID insumoId) {
        return presupuestoRepository.findDistinctByInsumoId(insumoId).stream()
                .map(this::aResumenResponse)
                .toList();
    }

    private PresupuestoResumenResponse aResumenResponse(Presupuesto p) {
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
                p.getEnviadoRevisionPor(),
                p.getFechaEnvioRevision(),
                p.getAprobadoPor(),
                p.getFechaAprobacion(),
                p.getMotivoRechazo(),
                p.getRechazadoPor(),
                p.getFechaRechazo(),
                p.getCapitulos().size(),
                totalItems,
                (int) versionPresupuestoRepository.countByPresupuestoId(p.getId())
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
        desembolsoCapituloRepository.deleteByCapituloId(capituloId);
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

    /**
     * RF-10 / UC-38: congela el detalle calculado en una versión inmutable y
     * registra la notificación al revisor, manteniendo la transición de estado
     * y la cola de revisión incorporadas desde main.
     */
    @Transactional
    public EnviarRevisionResponse enviarARevision(UUID id, EnviarRevisionRequest request, String usuarioSub) {
        Presupuesto p = buscarEditable(id);
        PresupuestoDetalleResponse detallePrevio = construirDetalleCalculado(p);
        String etiqueta = (request != null && request.etiqueta() != null && !request.etiqueta().isBlank())
                ? request.etiqueta().trim()
                : null;
        String comentario = (request != null && request.comentario() != null && !request.comentario().isBlank())
                ? request.comentario().trim()
                : null;
        String destinatario = (request != null && request.destinatarioSub() != null
                && !request.destinatarioSub().isBlank())
                ? request.destinatarioSub().trim()
                : DESTINATARIO_REVISION_POR_DEFECTO;
        OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC);

        VersionPresupuesto version = guardarSnapshotVersion(
                p, detallePrevio, etiqueta, comentario,
                EstadoPresupuesto.EN_REVISION.name(), usuarioSub, destinatario, ahora);

        p.setEstado(EstadoPresupuesto.EN_REVISION);
        p.setEnviadoRevisionPor(usuarioSub);
        p.setFechaEnvioRevision(ahora);
        p.setMotivoRechazo(null);
        p.setRechazadoPor(null);
        p.setFechaRechazo(null);

        PresupuestoDetalleResponse detalle = construirDetalleCalculado(presupuestoRepository.save(p));
        log.info("UC-38: versión {} congelada del presupuesto {} y notificación registrada al revisor {}",
                version.getNumeroVersion(), p.getCodigo(), destinatario);
        return new EnviarRevisionResponse(
                detalle,
                version.getNumeroVersion(),
                version.getCodigoPresupuesto(),
                version.getNombrePresupuesto(),
                version.getEstadoEnMomento(),
                version.getDestinatarioSub(),
                version.getFechaNotificacion(),
                version.getComentarioEnvio(),
                version.getSnapshotJson()
        );
    }

    @Transactional
    public VersionPresupuestoResumenResponse crearVersion(UUID id, CrearVersionRequest request, String usuarioSub) {
        Presupuesto p = buscarEditable(id);
        PresupuestoDetalleResponse detalle = construirDetalleCalculado(p);
        String comentario = (request.comentario() != null && !request.comentario().isBlank())
                ? request.comentario().trim()
                : null;
        VersionPresupuesto guardada = guardarSnapshotVersion(
                p, detalle, request.etiqueta().trim(), comentario,
                p.getEstado().name(), usuarioSub, null, null);
        return aVersionResumen(guardada);
    }

    @Transactional(readOnly = true)
    public List<VersionPresupuestoResumenResponse> listarVersiones(UUID presupuestoId) {
        buscarPorId(presupuestoId);
        return versionPresupuestoRepository.findByPresupuestoIdOrderByNumeroVersionDesc(presupuestoId).stream()
                .map(PresupuestoService::aVersionResumen)
                .toList();
    }

    @Transactional(readOnly = true)
    public VersionPresupuestoDetalleResponse obtenerVersion(UUID presupuestoId, UUID versionId) {
        VersionPresupuesto version = buscarVersionDePresupuesto(presupuestoId, versionId);
        return new VersionPresupuestoDetalleResponse(
                version.getId(),
                version.getPresupuestoId(),
                version.getNumeroVersion(),
                version.getEtiqueta(),
                version.getComentarioEnvio(),
                version.getEstadoEnMomento(),
                version.getCreadoPor(),
                version.getFechaCreacion(),
                version.getCostoDirecto(),
                version.getValorAdministracion(),
                version.getValorImprevistos(),
                version.getValorUtilidad(),
                version.getPrecioVenta(),
                deserializarSnapshot(version.getSnapshotJson()),
                version.getCodigoPresupuesto(),
                version.getNombrePresupuesto(),
                version.getDestinatarioSub(),
                version.getFechaNotificacion()
        );
    }

    @Transactional(readOnly = true)
    public ComparacionVersionesResponse compararVersiones(UUID presupuestoId, UUID versionAId, UUID versionBId) {
        VersionPresupuesto vA = buscarVersionDePresupuesto(presupuestoId, versionAId);
        VersionPresupuesto vB = buscarVersionDePresupuesto(presupuestoId, versionBId);

        PresupuestoDetalleResponse snapA = deserializarSnapshot(vA.getSnapshotJson());
        PresupuestoDetalleResponse snapB = deserializarSnapshot(vB.getSnapshotJson());

        Map<UUID, ItemSnapshotEntry> itemsA = indexarItemsSnapshot(snapA);
        Map<UUID, ItemSnapshotEntry> itemsB = indexarItemsSnapshot(snapB);

        List<ItemVersionCambio> agregados = new ArrayList<>();
        List<ItemVersionCambio> eliminados = new ArrayList<>();
        List<ItemVersionModificado> modificados = new ArrayList<>();

        for (var entry : itemsA.entrySet()) {
            ItemSnapshotEntry a = entry.getValue();
            ItemSnapshotEntry b = itemsB.get(entry.getKey());
            if (b == null) {
                eliminados.add(aCambio(a));
            } else if (huboCambioItem(a, b)) {
                BigDecimal difParcial = b.item().valorParcial().subtract(a.item().valorParcial())
                        .setScale(2, RoundingMode.HALF_UP);
                modificados.add(new ItemVersionModificado(
                        a.item().id(),
                        b.capituloCodigo(),
                        b.capituloNombre(),
                        b.item().codigoInsumo(),
                        b.item().descripcion(),
                        b.item().unidadMedida(),
                        a.item().cantidad(),
                        b.item().cantidad(),
                        a.item().precioUnitarioCopia(),
                        b.item().precioUnitarioCopia(),
                        a.item().valorParcial(),
                        b.item().valorParcial(),
                        difParcial
                ));
            }
        }

        for (var entry : itemsB.entrySet()) {
            if (!itemsA.containsKey(entry.getKey())) {
                agregados.add(aCambio(entry.getValue()));
            }
        }

        return new ComparacionVersionesResponse(
                presupuestoId,
                aVersionResumen(vA),
                aVersionResumen(vB),
                vB.getCostoDirecto().subtract(vA.getCostoDirecto()).setScale(2, RoundingMode.HALF_UP),
                vB.getPrecioVenta().subtract(vA.getPrecioVenta()).setScale(2, RoundingMode.HALF_UP),
                agregados,
                eliminados,
                modificados
        );
    }

    @Transactional
    public PresupuestoDetalleResponse rechazarPresupuesto(UUID id, String motivo, String revisorSub) {
        Presupuesto p = buscarPorId(id);
        if (p.getEstado() == EstadoPresupuesto.APROBADO) {
            throw new PresupuestoAprobadoException(
                    "El presupuesto " + p.getCodigo() + " ya está APROBADO y no puede rechazarse.");
        }
        if (p.getEstado() != EstadoPresupuesto.EN_REVISION) {
            throw new EstadoPresupuestoInvalidoException(
                    "Solo es posible rechazar presupuestos en estado EN_REVISION.");
        }
        p.setEstado(EstadoPresupuesto.BORRADOR);
        p.setMotivoRechazo(motivo.trim());
        p.setRechazadoPor(revisorSub);
        p.setFechaRechazo(OffsetDateTime.now(ZoneOffset.UTC));
        return construirDetalleCalculado(presupuestoRepository.save(p));
    }

    @Transactional(readOnly = true)
    public String exportarCsv(UUID id) {
        PresupuestoDetalleResponse d = obtenerDetalle(id);
        StringBuilder sb = new StringBuilder();
        sb.append("tipo_fila,orden,item_id,capitulo_codigo,capitulo_nombre,codigo_insumo,descripcion,unidad,cantidad,precio_unitario,valor_parcial\n");

        int consecutivo = 1;
        for (CapituloDetalleResponse cap : d.capitulos()) {
            for (ItemDetalleResponse item : cap.items()) {
                sb.append("ITEM,")
                        .append(consecutivo++).append(',')
                        .append(escaparCsv(item.id().toString())).append(',')
                        .append(escaparCsv(cap.codigo())).append(',')
                        .append(escaparCsv(cap.nombre())).append(',')
                        .append(escaparCsv(item.codigoInsumo())).append(',')
                        .append(escaparCsv(item.descripcion())).append(',')
                        .append(escaparCsv(item.unidadMedida())).append(',')
                        .append(item.cantidad().setScale(3, RoundingMode.HALF_UP).toPlainString()).append(',')
                        .append(item.precioUnitarioCopia().setScale(3, RoundingMode.HALF_UP).toPlainString()).append(',')
                        .append(item.valorParcial().setScale(2, RoundingMode.HALF_UP).toPlainString())
                        .append('\n');
            }
            sb.append("SUBTOTAL_CAPITULO,,,")
                    .append(escaparCsv(cap.codigo())).append(',')
                    .append(escaparCsv(cap.nombre())).append(",,,,,,")
                    .append(cap.subtotalCapitulo().setScale(2, RoundingMode.HALF_UP).toPlainString())
                    .append('\n');
        }

        appendResumenCsv(sb, "COSTO_DIRECTO", "Costo Directo Total", d.costoDirecto());
        appendResumenCsv(sb, "ADMINISTRACION", "Administración (" + d.porcentajeAdministracion().setScale(2, RoundingMode.HALF_UP).toPlainString() + "%)", d.valorAdministracion());
        appendResumenCsv(sb, "IMPREVISTOS", "Imprevistos (" + d.porcentajeImprevistos().setScale(2, RoundingMode.HALF_UP).toPlainString() + "%)", d.valorImprevistos());
        appendResumenCsv(sb, "UTILIDAD", "Utilidad (" + d.porcentajeUtilidad().setScale(2, RoundingMode.HALF_UP).toPlainString() + "%)", d.valorUtilidad());
        appendResumenCsv(sb, "PRECIO_VENTA", "Precio de Venta Total", d.precioVenta());
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public DesembolsosResponse obtenerDesembolsos(UUID id) {
        PresupuestoDetalleResponse detalle = obtenerDetalle(id);
        List<DesembolsoCapitulo> guardados = desembolsoCapituloRepository.findByPresupuestoIdOrderBySemanaAsc(id);

        int semanas = guardados.stream()
                .mapToInt(DesembolsoCapitulo::getSemana)
                .max()
                .orElse(4);

        Map<UUID, Map<Integer, BigDecimal>> porCapYSemana = new HashMap<>();
        for (DesembolsoCapitulo dc : guardados) {
            porCapYSemana.computeIfAbsent(dc.getCapituloId(), k -> new HashMap<>())
                    .put(dc.getSemana(), dc.getPorcentajeAvance());
        }

        List<DesembolsosRequest.CapituloDesembolsoInput> inputs = new ArrayList<>();
        for (CapituloDetalleResponse cap : detalle.capitulos()) {
            Map<Integer, BigDecimal> mapa = porCapYSemana.get(cap.id());
            List<BigDecimal> porcentajes;
            if (mapa != null && !mapa.isEmpty()) {
                porcentajes = new ArrayList<>(semanas);
                for (int s = 1; s <= semanas; s++) {
                    porcentajes.add(mapa.getOrDefault(s, BigDecimal.ZERO));
                }
            } else {
                porcentajes = distribucionEquitativa(semanas);
            }
            inputs.add(new DesembolsosRequest.CapituloDesembolsoInput(
                    cap.id().toString(),
                    cap.codigo(),
                    cap.nombre(),
                    cap.subtotalCapitulo(),
                    porcentajes
            ));
        }

        return calculoMotorClient.calcularDesembolsos(new DesembolsosRequest(semanas, inputs));
    }

    @Transactional
    public DesembolsosResponse guardarDesembolsos(UUID id, DesembolsoConfigRequest request) {
        Presupuesto p = buscarEditable(id);
        PresupuestoDetalleResponse detalle = construirDetalleCalculado(p);

        Map<UUID, CapituloDetalleResponse> capsDetalle = new LinkedHashMap<>();
        for (CapituloDetalleResponse cap : detalle.capitulos()) {
            capsDetalle.put(cap.id(), cap);
        }

        Map<UUID, List<BigDecimal>> porcentajesPorCap = new HashMap<>();
        for (var capCfg : request.capitulos()) {
            if (!capsDetalle.containsKey(capCfg.capituloId())) {
                throw new RecursoNoEncontradoException("Capítulo no encontrado en el presupuesto: " + capCfg.capituloId());
            }
            porcentajesPorCap.put(capCfg.capituloId(), capCfg.porcentajesPorSemana());
        }

        List<DesembolsosRequest.CapituloDesembolsoInput> inputs = new ArrayList<>();
        for (CapituloDetalleResponse cap : detalle.capitulos()) {
            List<BigDecimal> pcts = porcentajesPorCap.getOrDefault(cap.id(), distribucionEquitativa(request.semanas()));
            inputs.add(new DesembolsosRequest.CapituloDesembolsoInput(
                    cap.id().toString(),
                    cap.codigo(),
                    cap.nombre(),
                    cap.subtotalCapitulo(),
                    pcts
            ));
        }

        DesembolsosResponse calculado = calculoMotorClient.calcularDesembolsos(
                new DesembolsosRequest(request.semanas(), inputs));

        desembolsoCapituloRepository.deleteByPresupuestoId(id);
        List<DesembolsoCapitulo> entidades = new ArrayList<>();
        for (var capRes : calculado.capitulos()) {
            UUID capUuid = UUID.fromString(capRes.capituloId());
            for (int s = 0; s < calculado.semanas(); s++) {
                DesembolsoCapitulo dc = new DesembolsoCapitulo();
                dc.setPresupuestoId(id);
                dc.setCapituloId(capUuid);
                dc.setSemana(s + 1);
                dc.setPorcentajeAvance(capRes.porcentajesPorSemana().get(s));
                entidades.add(dc);
            }
        }
        desembolsoCapituloRepository.saveAll(entidades);

        return calculado;
    }

    @Transactional
    public PresupuestoDetalleResponse aprobarPresupuesto(UUID id, String revisorSub) {
        Presupuesto p = buscarPorId(id);
        if (p.getEstado() == EstadoPresupuesto.APROBADO) {
            throw new PresupuestoAprobadoException(
                    "El presupuesto " + p.getCodigo() + " ya está APROBADO y no permite modificaciones.");
        }
        p.setEstado(EstadoPresupuesto.APROBADO);
        p.setAprobadoPor(revisorSub);
        p.setFechaAprobacion(OffsetDateTime.now(ZoneOffset.UTC));
        p.setMotivoRechazo(null);
        p.setRechazadoPor(null);
        p.setFechaRechazo(null);
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
        if (p.getEstado() == EstadoPresupuesto.EN_REVISION) {
            throw new PresupuestoNoEditableException(
                    "El presupuesto " + p.getCodigo() + " está EN_REVISION y no puede editarse hasta que sea rechazado o aprobado.");
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
                p.getEnviadoRevisionPor(),
                p.getFechaEnvioRevision(),
                p.getAprobadoPor(),
                p.getFechaAprobacion(),
                p.getMotivoRechazo(),
                p.getRechazadoPor(),
                p.getFechaRechazo(),
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

    private VersionPresupuesto guardarSnapshotVersion(
            Presupuesto p,
            PresupuestoDetalleResponse detalle,
            String etiquetaOpcional,
            String comentario,
            String estadoMomento,
            String usuarioSub,
            String destinatarioSub,
            OffsetDateTime fechaNotificacion) {
        int siguienteVersion = versionPresupuestoRepository.findMaxNumeroVersionByPresupuestoId(p.getId()) + 1;
        String etiqueta = (etiquetaOpcional != null && !etiquetaOpcional.isBlank())
                ? etiquetaOpcional
                : "Versión v" + siguienteVersion;

        VersionPresupuesto version = new VersionPresupuesto();
        version.setPresupuestoId(p.getId());
        version.setNumeroVersion(siguienteVersion);
        version.setEtiqueta(etiqueta);
        version.setComentarioEnvio(comentario);
        version.setEstadoEnMomento(estadoMomento);
        version.setCreadoPor(usuarioSub);
        version.setFechaCreacion(OffsetDateTime.now(ZoneOffset.UTC));
        version.setCodigoPresupuesto(p.getCodigo());
        version.setNombrePresupuesto(p.getNombre());
        version.setDestinatarioSub(destinatarioSub);
        version.setFechaNotificacion(fechaNotificacion);
        version.setCostoDirecto(detalle.costoDirecto());
        version.setValorAdministracion(detalle.valorAdministracion());
        version.setValorImprevistos(detalle.valorImprevistos());
        version.setValorUtilidad(detalle.valorUtilidad());
        version.setPrecioVenta(detalle.precioVenta());
        try {
            version.setSnapshotJson(objectMapper.writeValueAsString(detalle));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No fue posible serializar el snapshot de versión.", ex);
        }
        return versionPresupuestoRepository.save(version);
    }

    private VersionPresupuesto buscarVersionDePresupuesto(UUID presupuestoId, UUID versionId) {
        return versionPresupuestoRepository.findByIdAndPresupuestoId(versionId, presupuestoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Versión " + versionId + " no encontrada para el presupuesto " + presupuestoId));
    }

    private PresupuestoDetalleResponse deserializarSnapshot(String json) {
        try {
            return objectMapper.readValue(json, PresupuestoDetalleResponse.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No fue posible leer el snapshot de versión guardado.", ex);
        }
    }

    private static VersionPresupuestoResumenResponse aVersionResumen(VersionPresupuesto v) {
        return new VersionPresupuestoResumenResponse(
                v.getId(),
                v.getPresupuestoId(),
                v.getNumeroVersion(),
                v.getEtiqueta(),
                v.getComentarioEnvio(),
                v.getEstadoEnMomento(),
                v.getCreadoPor(),
                v.getFechaCreacion(),
                v.getCostoDirecto(),
                v.getValorAdministracion(),
                v.getValorImprevistos(),
                v.getValorUtilidad(),
                v.getPrecioVenta(),
                v.getCodigoPresupuesto(),
                v.getNombrePresupuesto(),
                v.getDestinatarioSub(),
                v.getFechaNotificacion()
        );
    }

    private static Map<UUID, ItemSnapshotEntry> indexarItemsSnapshot(PresupuestoDetalleResponse snapshot) {
        Map<UUID, ItemSnapshotEntry> map = new LinkedHashMap<>();
        if (snapshot.capitulos() == null) {
            return map;
        }
        for (CapituloDetalleResponse cap : snapshot.capitulos()) {
            if (cap.items() == null) continue;
            for (ItemDetalleResponse item : cap.items()) {
                map.put(item.id(), new ItemSnapshotEntry(cap.codigo(), cap.nombre(), item));
            }
        }
        return map;
    }

    private static boolean huboCambioItem(ItemSnapshotEntry a, ItemSnapshotEntry b) {
        return a.item().cantidad().compareTo(b.item().cantidad()) != 0
                || a.item().precioUnitarioCopia().compareTo(b.item().precioUnitarioCopia()) != 0
                || a.item().valorParcial().compareTo(b.item().valorParcial()) != 0
                || !a.capituloCodigo().equals(b.capituloCodigo())
                || !a.item().descripcion().equals(b.item().descripcion());
    }

    private static ItemVersionCambio aCambio(ItemSnapshotEntry entry) {
        return new ItemVersionCambio(
                entry.item().id(),
                entry.capituloCodigo(),
                entry.capituloNombre(),
                entry.item().codigoInsumo(),
                entry.item().descripcion(),
                entry.item().unidadMedida(),
                entry.item().cantidad(),
                entry.item().precioUnitarioCopia(),
                entry.item().valorParcial()
        );
    }

    private static void appendResumenCsv(StringBuilder sb, String tipo, String etiqueta, BigDecimal valor) {
        sb.append(tipo).append(",,,,,")
                .append(escaparCsv(etiqueta)).append(",,,,,")
                .append(valor.setScale(2, RoundingMode.HALF_UP).toPlainString())
                .append('\n');
    }

    private static String escaparCsv(String valor) {
        if (valor == null) {
            return "";
        }
        if (valor.contains(",") || valor.contains("\"") || valor.contains("\n") || valor.contains("\r")) {
            return "\"" + valor.replace("\"", "\"\"") + "\"";
        }
        return valor;
    }

    private static List<BigDecimal> distribucionEquitativa(int semanas) {
        List<BigDecimal> lista = new ArrayList<>(semanas);
        BigDecimal base = CIEN.divide(BigDecimal.valueOf(semanas), 4, RoundingMode.DOWN);
        BigDecimal suma = BigDecimal.ZERO;
        for (int i = 0; i < semanas; i++) {
            if (i == semanas - 1) {
                lista.add(CIEN.subtract(suma).setScale(4, RoundingMode.HALF_UP));
            } else {
                lista.add(base);
                suma = suma.add(base);
            }
        }
        return lista;
    }

    private record ItemSnapshotEntry(String capituloCodigo, String capituloNombre, ItemDetalleResponse item) {}

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
