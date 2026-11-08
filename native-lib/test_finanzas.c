#include "finanzas.h"
#include <math.h>
#include <stdio.h>
#include <stdlib.h>

static int fallos = 0;
static int ejecutados = 0;

static void verificar_casi_igual(const char *id, const char *nombre,
                                 double esperado, double obtenido, double tol) {
    ejecutados++;
    double diff = fabs(esperado - obtenido);
    if (diff <= tol) {
        printf("[PASS] %s - %s | Esperado: %.6f | Obtenido: %.6f\n",
               id, nombre, esperado, obtenido);
    } else {
        fallos++;
        printf("[FAIL] %s - %s | Esperado: %.6f | Obtenido: %.6f | Diff: %.9f\n",
               id, nombre, esperado, obtenido, diff);
    }
}

static void verificar_entero(const char *id, const char *nombre,
                             int esperado, int obtenido) {
    ejecutados++;
    if (esperado == obtenido) {
        printf("[PASS] %s - %s | Codigo Esperado: %d | Obtenido: %d\n",
               id, nombre, esperado, obtenido);
    } else {
        fallos++;
        printf("[FAIL] %s - %s | Codigo Esperado: %d | Obtenido: %d\n",
               id, nombre, esperado, obtenido);
    }
}

int main(void) {
    printf("=== SUITE AISLADA DE PRUEBAS EN C (libfinanzas.so) - RQ-03 ===\n");

    /* Caso 0: Spike JNA suma */
    verificar_casi_igual("SPIKE-00", "fin_ping_suma(25.5, 14.5)",
                         40.0, fin_ping_suma(25.5, 14.5), 1e-9);

    /* CR-08: VPN Flujo Estandar */
    double flujos_cr08[] = {-1000.0, 500.0, 500.0, 500.0};
    verificar_casi_igual("CR-08", "VPN al 10% de [-1000, 500, 500, 500]",
                         243.425995, fin_vpn(0.10, flujos_cr08, 4), 1e-5);

    /* CR-09: VPN Tasa Cero */
    double flujos_cr09[] = {-1000.0, 400.0, 350.0, 500.0};
    verificar_casi_igual("CR-09", "VPN al 0% de [-1000, 400, 350, 500]",
                         250.0, fin_vpn(0.0, flujos_cr09, 4), 1e-9);

    /* CR-10: TIR Flujo Clasico 1 Periodo */
    double flujos_cr10[] = {-1000.0, 1100.0};
    double tir_out = 0.0;
    int iter_out = 0;
    int rc = fin_tir(flujos_cr10, 2, 1e-9, 1000, &tir_out, &iter_out);
    verificar_entero("CR-10-RC", "Retorno TIR [-1000, 1100]", FIN_OK, rc);
    verificar_casi_igual("CR-10-VAL", "Valor TIR [-1000, 1100]", 0.100000, tir_out, 1e-6);

    /* CR-11: TIR Obra 90 Dias */
    double flujos_cr11[] = {-10000.0, 3500.0, 4000.0, 4500.0};
    rc = fin_tir(flujos_cr11, 4, 1e-9, 1000, &tir_out, &iter_out);
    verificar_entero("CR-11-RC", "Retorno TIR [-10000, 3500, 4000, 4500]", FIN_OK, rc);
    verificar_casi_igual("CR-11-VAL", "Valor TIR Obra 90 Dias (>= 4 dec)", 0.092825, tir_out, 1e-5);

    /* CR-12: Caso Limite - Flujo Sin Cambio de Signo */
    double flujos_cr12[] = {-1000.0, -500.0, -200.0};
    rc = fin_tir(flujos_cr12, 3, 1e-9, 1000, &tir_out, &iter_out);
    verificar_entero("CR-12", "Caso Limite: Flujo sin cambio de signo devuelve FIN_ERR_SIN_CAMBIO_SIGNO (-3)",
                     FIN_ERR_SIN_CAMBIO_SIGNO, rc);

    /* CR-13: Caso Limite - Flujo No Convergente */
    double flujos_cr13[] = {-1000.0, 500.0, -500.0};
    rc = fin_tir(flujos_cr13, 3, 1e-12, 50, &tir_out, &iter_out);
    verificar_entero("CR-13-RC", "Caso Limite: Flujo sin raiz real devuelve FIN_ERR_NO_CONVERGIO (-4)",
                     FIN_ERR_NO_CONVERGIO, rc);
    verificar_entero("CR-13-ITER", "Caso Limite: Reporta iteraciones alcanzadas (50)", 50, iter_out);

    /* CR-14: Tabla de Amortizacion Sistema Frances */
    double cuotas[12];
    rc = fin_amortizacion(10000.0, 0.02, 3, cuotas);
    verificar_entero("CR-14-RC", "Retorno Amortizacion 10000 al 2% en 3 periodos", FIN_OK, rc);
    verificar_casi_igual("CR-14-CUOTA", "Cuota fija periodo 1", 3467.546726, cuotas[0], 1e-4);
    verificar_casi_igual("CR-14-SALDO", "Saldo final en periodo 3 es 0.00", 0.0, cuotas[11], 1e-9);

    printf("==============================================================\n");
    printf("Resumen C: %d pruebas ejecutadas, %d fallos.\n", ejecutados, fallos);
    return (fallos == 0) ? EXIT_SUCCESS : EXIT_FAILURE;
}
