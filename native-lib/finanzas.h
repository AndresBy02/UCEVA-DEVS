#ifndef FINANZAS_H
#define FINANZAS_H

#ifdef __cplusplus
extern "C" {
#endif

#define FIN_OK                     0
#define FIN_ERR_PARAMETROS_NULOS  -1
#define FIN_ERR_DIMENSION_INVALIDA -2
#define FIN_ERR_SIN_CAMBIO_SIGNO  -3
#define FIN_ERR_NO_CONVERGIO      -4
#define FIN_ERR_TASA_INVALIDA     -5

/* Función de verificación de enlace JNA (Spike / Healthcheck) */
double fin_ping_suma(double a, double b);

/* Valor presente neto: fórmula cerrada, una sola pasada. */
double fin_vpn(double tasa, const double *flujos, int n);

/* Tasa interna de retorno por aproximaciones sucesivas.
   Devuelve 0 si convergió, negativo si no. La tasa sale por
   tir_out y el conteo de iteraciones por iter_out: el número de
   iteraciones ES parte del resultado, no un dato de depuración. */
int fin_tir(const double *flujos, int n, double tol, int max_iter,
            double *tir_out, int *iter_out);

/* Tabla de amortización: llena el arreglo cuotas (ya reservado por
   quien llama) con periodos filas de cuota, interés, abono y saldo. */
int fin_amortizacion(double capital, double tasa_periodo, int periodos,
                     double *cuotas_out);

#ifdef __cplusplus
}
#endif

#endif /* FINANZAS_H */
