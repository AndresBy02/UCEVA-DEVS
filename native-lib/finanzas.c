#include "finanzas.h"
#include <math.h>
#include <stddef.h>

double fin_ping_suma(double a, double b) {
    return a + b;
}

double fin_vpn(double tasa, const double *flujos, int n) {
    if (flujos == NULL || n <= 0 || tasa <= -1.0) {
        return NAN;
    }
    double vpn = 0.0;
    double factor = 1.0;
    double base = 1.0 + tasa;
    for (int t = 0; t < n; t++) {
        vpn += flujos[t] / factor;
        factor *= base;
    }
    return vpn;
}

static void evaluar_vpn_y_derivada(double tasa, const double *flujos, int n,
                                   double *f_out, double *df_out) {
    double f = 0.0;
    double df = 0.0;
    double base = 1.0 + tasa;
    double factor = 1.0; /* (1 + tasa)^t */
    for (int t = 0; t < n; t++) {
        f += flujos[t] / factor;
        if (t > 0) {
            df -= ((double)t * flujos[t]) / (factor * base);
        }
        factor *= base;
    }
    *f_out = f;
    *df_out = df;
}

int fin_tir(const double *flujos, int n, double tol, int max_iter,
            double *tir_out, int *iter_out) {
    if (flujos == NULL || tir_out == NULL || iter_out == NULL) {
        return FIN_ERR_PARAMETROS_NULOS;
    }
    if (n < 2 || tol <= 0.0 || max_iter <= 0) {
        *iter_out = 0;
        return FIN_ERR_DIMENSION_INVALIDA;
    }

    int tiene_positivo = 0;
    int tiene_negativo = 0;
    for (int i = 0; i < n; i++) {
        if (flujos[i] > 0.0) tiene_positivo = 1;
        if (flujos[i] < 0.0) tiene_negativo = 1;
    }
    if (!tiene_positivo || !tiene_negativo) {
        *iter_out = 0;
        return FIN_ERR_SIN_CAMBIO_SIGNO;
    }

    /* Fase 1: Newton-Raphson desde r0 = 0.10 (10%) */
    double r = 0.10;
    int iter = 0;
    while (iter < max_iter) {
        iter++;
        double f = 0.0, df = 0.0;
        evaluar_vpn_y_derivada(r, flujos, n, &f, &df);

        if (fabs(f) <= tol) {
            *tir_out = r;
            *iter_out = iter;
            return FIN_OK;
        }

        if (fabs(df) < 1e-14 || isnan(f) || isinf(f)) {
            break; /* Pasar a salvaguarda de bisección si la derivada se anula */
        }

        double r_next = r - (f / df);
        if (r_next <= -0.9999) {
            r_next = -0.99;
        } else if (r_next > 100.0) {
            r_next = 10.0;
        }

        if (fabs(r_next - r) <= tol * 0.1) {
            double f_check = fin_vpn(r_next, flujos, n);
            if (fabs(f_check) <= tol) {
                *tir_out = r_next;
                *iter_out = iter;
                return FIN_OK;
            }
        }
        r = r_next;
    }

    /* Fase 2: Salvaguarda de Bisección si Newton-Raphson osciló y quedan iteraciones */
    double low = -0.90;
    double high = 5.0;
    double f_low = fin_vpn(low, flujos, n);
    double f_high = fin_vpn(high, flujos, n);

    if (f_low * f_high < 0.0) {
        while (iter < max_iter) {
            iter++;
            double mid = 0.5 * (low + high);
            double f_mid = fin_vpn(mid, flujos, n);
            if (fabs(f_mid) <= tol) {
                *tir_out = mid;
                *iter_out = iter;
                return FIN_OK;
            }
            if (f_low * f_mid < 0.0) {
                high = mid;
                f_high = f_mid;
            } else {
                low = mid;
                f_low = f_mid;
            }
        }
    }

    *iter_out = max_iter;
    return FIN_ERR_NO_CONVERGIO;
}

int fin_amortizacion(double capital, double tasa_periodo, int periodos,
                     double *cuotas_out) {
    if (cuotas_out == NULL) {
        return FIN_ERR_PARAMETROS_NULOS;
    }
    if (capital <= 0.0 || periodos <= 0) {
        return FIN_ERR_DIMENSION_INVALIDA;
    }
    if (tasa_periodo < 0.0) {
        return FIN_ERR_TASA_INVALIDA;
    }

    double cuota;
    if (tasa_periodo == 0.0) {
        cuota = capital / (double)periodos;
    } else {
        double factor = pow(1.0 + tasa_periodo, (double)periodos);
        cuota = capital * (tasa_periodo * factor) / (factor - 1.0);
    }

    double saldo = capital;
    for (int p = 0; p < periodos; p++) {
        double interes = saldo * tasa_periodo;
        double abono = cuota - interes;
        if (p == periodos - 1) {
            abono = saldo;
            cuota = interes + abono;
            saldo = 0.0;
        } else {
            saldo -= abono;
        }
        int base_idx = p * 4;
        cuotas_out[base_idx + 0] = cuota;
        cuotas_out[base_idx + 1] = interes;
        cuotas_out[base_idx + 2] = abono;
        cuotas_out[base_idx + 3] = saldo;
    }

    return FIN_OK;
}
