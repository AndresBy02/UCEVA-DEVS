package co.edu.uceva.calculoservice.jna;

import com.sun.jna.Library;
import com.sun.jna.ptr.DoubleByReference;
import com.sun.jna.ptr.IntByReference;

public interface FinanzasLibrary extends Library {

    int FIN_OK = 0;
    int FIN_ERR_PARAMETROS_NULOS = -1;
    int FIN_ERR_DIMENSION_INVALIDA = -2;
    int FIN_ERR_SIN_CAMBIO_SIGNO = -3;
    int FIN_ERR_NO_CONVERGIO = -4;
    int FIN_ERR_TASA_INVALIDA = -5;

    double fin_ping_suma(double a, double b);

    double fin_vpn(double tasa, double[] flujos, int n);

    int fin_tir(double[] flujos, int n, double tol, int maxIter,
                DoubleByReference tirOut, IntByReference iterOut);

    int fin_amortizacion(double capital, double tasaPeriodo, int periodos,
                         double[] cuotasOut);
}
