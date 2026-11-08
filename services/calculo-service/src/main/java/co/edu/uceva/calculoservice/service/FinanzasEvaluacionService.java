package co.edu.uceva.calculoservice.service;

import co.edu.uceva.calculoservice.dto.EvaluacionFinancieraRequest;
import co.edu.uceva.calculoservice.dto.EvaluacionFinancieraResponse;
import co.edu.uceva.calculoservice.dto.EvaluacionFinancieraResponse.AmortizacionResult;
import co.edu.uceva.calculoservice.dto.EvaluacionFinancieraResponse.FilaAmortizacion;
import co.edu.uceva.calculoservice.dto.EvaluacionFinancieraResponse.TirResult;
import co.edu.uceva.calculoservice.dto.SpikeJnaResponse;
import co.edu.uceva.calculoservice.jna.FinanzasLibrary;
import com.sun.jna.Native;
import com.sun.jna.ptr.DoubleByReference;
import com.sun.jna.ptr.IntByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class FinanzasEvaluacionService {

    private static final Logger log = LoggerFactory.getLogger(FinanzasEvaluacionService.class);
    private static final BigDecimal CIEN = new BigDecimal("100");

    @Value("${calculo.finanzas.modo:AUTO}")
    private String modoConfigurado;

    private volatile FinanzasLibrary nativeLib;
    private volatile boolean cargaIntentada = false;

    public synchronized FinanzasLibrary obtenerBibliotecaNativa() {
        if (!cargaIntentada) {
            cargaIntentada = true;
            try {
                nativeLib = Native.load("finanzas", FinanzasLibrary.class);
                log.info("Biblioteca nativa libfinanzas.so cargada exitosamente via JNA.");
            } catch (UnsatisfiedLinkError | RuntimeException e) {
                log.warn("No se pudo cargar libfinanzas.so en este entorno ({}). Se usara fallback Java si el modo es AUTO.", e.getMessage());
                nativeLib = null;
            }
        }
        return nativeLib;
    }

    public SpikeJnaResponse ejecutarSpikeJna(double a, double b) {
        FinanzasLibrary lib = obtenerBibliotecaNativa();
        String arch = System.getProperty("os.arch") + " / " + System.getProperty("os.name");
        if (lib != null) {
            double resultado = lib.fin_ping_suma(a, b);
            return new SpikeJnaResponse(
                    true,
                    "libfinanzas.so",
                    a,
                    b,
                    resultado,
                    arch,
                    "Biblioteca nativa libfinanzas.so invocada exitosamente via JNA"
            );
        }
        return new SpikeJnaResponse(
                false,
                "libfinanzas.so (no disponible en este SO anfitrion, usando respaldo Java)",
                a,
                b,
                a + b,
                arch,
                "Respaldo Java activo (ejecute dentro del contenedor Docker Linux para usar libfinanzas.so)"
        );
    }

    public EvaluacionFinancieraResponse evaluarProyecto(EvaluacionFinancieraRequest request) {
        boolean usarNativo = debeUsarNativo();
        String motor = usarNativo ? "NATIVE_C_LIBFINANZAS" : "JAVA_BASELINE_FALLBACK";

        double tasaDescuento = request.tasaDescuento() != null ? request.tasaDescuento().doubleValue() : 0.0;
        List<BigDecimal> flujosBd = request.flujosCaja() != null ? request.flujosCaja() : List.of();
        double[] flujos = new double[flujosBd.size()];
        for (int i = 0; i < flujosBd.size(); i++) {
            flujos[i] = flujosBd.get(i).doubleValue();
        }

        double tol = request.toleranciaTir() != null ? request.toleranciaTir() : 1e-9;
        int maxIter = request.maxIteracionesTir() != null ? request.maxIteracionesTir() : 1000;

        /* 1. Cálculo de VPN (Aduana BigDecimal <-> double) */
        double vpnDouble = usarNativo
                ? obtenerBibliotecaNativa().fin_vpn(tasaDescuento, flujos, flujos.length)
                : calcularVpnJava(tasaDescuento, flujos);

        BigDecimal vpnExacto = Double.isNaN(vpnDouble) || Double.isInfinite(vpnDouble)
                ? null
                : BigDecimal.valueOf(vpnDouble).setScale(2, RoundingMode.HALF_UP);

        /* 2. Cálculo de TIR (Aduana BigDecimal <-> double) */
        TirResult tirResult = usarNativo
                ? calcularTirNativo(flujos, tol, maxIter)
                : calcularTirJava(flujos, tol, maxIter);

        /* 3. Cálculo de Tabla de Amortización */
        AmortizacionResult amortizacionResult = null;
        if (request.financiacion() != null
                && request.financiacion().capital() != null
                && request.financiacion().periodos() != null
                && request.financiacion().periodos() > 0) {
            amortizacionResult = usarNativo
                    ? calcularAmortizacionNativo(request.financiacion())
                    : calcularAmortizacionJava(request.financiacion());
        }

        return new EvaluacionFinancieraResponse(motor, vpnExacto, tirResult, amortizacionResult);
    }

    private boolean debeUsarNativo() {
        if ("JAVA".equalsIgnoreCase(modoConfigurado)) {
            return false;
        }
        FinanzasLibrary lib = obtenerBibliotecaNativa();
        if ("NATIVE".equalsIgnoreCase(modoConfigurado) && lib == null) {
            throw new IllegalStateException("Modo NATIVE exigido pero libfinanzas.so no pudo cargarse");
        }
        return lib != null;
    }

    private TirResult calcularTirNativo(double[] flujos, double tol, int maxIter) {
        DoubleByReference tirOut = new DoubleByReference(0.0);
        IntByReference iterOut = new IntByReference(0);
        int rc = obtenerBibliotecaNativa().fin_tir(flujos, flujos.length, tol, maxIter, tirOut, iterOut);
        return construirTirResult(rc, tirOut.getValue(), iterOut.getValue());
    }

    public TirResult calcularTirJava(double[] flujos, double tol, int maxIter) {
        if (flujos == null || flujos.length < 2 || tol <= 0.0 || maxIter <= 0) {
            return construirTirResult(FinanzasLibrary.FIN_ERR_DIMENSION_INVALIDA, 0.0, 0);
        }
        boolean tienePos = false;
        boolean tieneNeg = false;
        for (double v : flujos) {
            if (v > 0.0) tienePos = true;
            if (v < 0.0) tieneNeg = true;
        }
        if (!tienePos || !tieneNeg) {
            return construirTirResult(FinanzasLibrary.FIN_ERR_SIN_CAMBIO_SIGNO, 0.0, 0);
        }

        double r = 0.10;
        int iter = 0;
        while (iter < maxIter) {
            iter++;
            double f = 0.0;
            double df = 0.0;
            double base = 1.0 + r;
            double factor = 1.0;
            for (int t = 0; t < flujos.length; t++) {
                f += flujos[t] / factor;
                if (t > 0) {
                    df -= (t * flujos[t]) / (factor * base);
                }
                factor *= base;
            }
            if (Math.abs(f) <= tol) {
                return construirTirResult(FinanzasLibrary.FIN_OK, r, iter);
            }
            if (Math.abs(df) < 1e-14 || Double.isNaN(f) || Double.isInfinite(f)) {
                break;
            }
            double rNext = r - (f / df);
            if (rNext <= -0.9999) rNext = -0.99;
            else if (rNext > 100.0) rNext = 10.0;

            if (Math.abs(rNext - r) <= tol * 0.1) {
                double fCheck = calcularVpnJava(rNext, flujos);
                if (Math.abs(fCheck) <= tol) {
                    return construirTirResult(FinanzasLibrary.FIN_OK, rNext, iter);
                }
            }
            r = rNext;
        }

        double low = -0.90;
        double high = 5.0;
        double fLow = calcularVpnJava(low, flujos);
        double fHigh = calcularVpnJava(high, flujos);
        if (fLow * fHigh < 0.0) {
            while (iter < maxIter) {
                iter++;
                double mid = 0.5 * (low + high);
                double fMid = calcularVpnJava(mid, flujos);
                if (Math.abs(fMid) <= tol) {
                    return construirTirResult(FinanzasLibrary.FIN_OK, mid, iter);
                }
                if (fLow * fMid < 0.0) {
                    high = mid;
                    fHigh = fMid;
                } else {
                    low = mid;
                    fLow = fMid;
                }
            }
        }

        return construirTirResult(FinanzasLibrary.FIN_ERR_NO_CONVERGIO, 0.0, maxIter);
    }

    private TirResult construirTirResult(int rc, double tirVal, int iteraciones) {
        if (rc == FinanzasLibrary.FIN_OK) {
            BigDecimal tirBd = BigDecimal.valueOf(tirVal).setScale(6, RoundingMode.HALF_UP);
            BigDecimal tirPorc = tirBd.multiply(CIEN).setScale(4, RoundingMode.HALF_UP);
            return new TirResult(
                    true,
                    "CONVERGIO",
                    rc,
                    tirBd,
                    tirPorc,
                    iteraciones,
                    "Convergencia alcanzada en " + iteraciones + " iteraciones"
            );
        }
        String detalle = (rc == FinanzasLibrary.FIN_ERR_SIN_CAMBIO_SIGNO)
                ? "no convergió (el flujo de caja no tiene cambio de signo)"
                : "no convergió (alcanzado el límite de " + iteraciones + " iteraciones)";
        return new TirResult(
                false,
                "no convergió",
                rc,
                null,
                null,
                iteraciones,
                detalle
        );
    }

    public double calcularVpnJava(double tasa, double[] flujos) {
        if (flujos == null || flujos.length == 0 || tasa <= -1.0) {
            return Double.NaN;
        }
        double vpn = 0.0;
        double factor = 1.0;
        double base = 1.0 + tasa;
        for (double flujo : flujos) {
            vpn += flujo / factor;
            factor *= base;
        }
        return vpn;
    }

    private AmortizacionResult calcularAmortizacionNativo(EvaluacionFinancieraRequest.FinanciacionRequest fin) {
        int periodos = fin.periodos();
        double capital = fin.capital().doubleValue();
        double tasa = fin.tasaPeriodo() != null ? fin.tasaPeriodo().doubleValue() : 0.0;
        double[] cuotasOut = new double[periodos * 4];

        int rc = obtenerBibliotecaNativa().fin_amortizacion(capital, tasa, periodos, cuotasOut);
        if (rc != FinanzasLibrary.FIN_OK) {
            throw new IllegalArgumentException("Parametros invalidos para amortizacion (codigo C: " + rc + ")");
        }
        return empaquetarAmortizacion(fin.capital(), fin.tasaPeriodo(), periodos, cuotasOut);
    }

    private AmortizacionResult calcularAmortizacionJava(EvaluacionFinancieraRequest.FinanciacionRequest fin) {
        int periodos = fin.periodos();
        double capital = fin.capital().doubleValue();
        double tasa = fin.tasaPeriodo() != null ? fin.tasaPeriodo().doubleValue() : 0.0;
        double[] cuotasOut = new double[periodos * 4];

        double cuota;
        if (tasa == 0.0) {
            cuota = capital / periodos;
        } else {
            double factor = Math.pow(1.0 + tasa, periodos);
            cuota = capital * (tasa * factor) / (factor - 1.0);
        }
        double saldo = capital;
        for (int p = 0; p < periodos; p++) {
            double interes = saldo * tasa;
            double abono = cuota - interes;
            if (p == periodos - 1) {
                abono = saldo;
                cuota = interes + abono;
                saldo = 0.0;
            } else {
                saldo -= abono;
            }
            int idx = p * 4;
            cuotasOut[idx] = cuota;
            cuotasOut[idx + 1] = interes;
            cuotasOut[idx + 2] = abono;
            cuotasOut[idx + 3] = saldo;
        }
        return empaquetarAmortizacion(fin.capital(), fin.tasaPeriodo(), periodos, cuotasOut);
    }

    private AmortizacionResult empaquetarAmortizacion(BigDecimal capital, BigDecimal tasa, int periodos, double[] raw) {
        List<FilaAmortizacion> filas = new ArrayList<>(periodos);
        for (int p = 0; p < periodos; p++) {
            int idx = p * 4;
            filas.add(new FilaAmortizacion(
                    p + 1,
                    BigDecimal.valueOf(raw[idx]).setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(raw[idx + 1]).setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(raw[idx + 2]).setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(raw[idx + 3]).setScale(2, RoundingMode.HALF_UP)
            ));
        }
        BigDecimal cuotaFija = filas.isEmpty() ? BigDecimal.ZERO : filas.get(0).cuota();
        return new AmortizacionResult(
                capital.setScale(2, RoundingMode.HALF_UP),
                tasa != null ? tasa : BigDecimal.ZERO,
                periodos,
                cuotaFija,
                filas
        );
    }
}
