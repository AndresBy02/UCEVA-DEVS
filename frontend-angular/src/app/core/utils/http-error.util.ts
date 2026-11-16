export function extraerErrorHttp(err: unknown, fallback = 'Error de comunicación con el servicio'): string {
  const e = err as { status?: number; error?: { detail?: string; title?: string } };
  const detail = e?.error?.detail ?? e?.error?.title;
  switch (e?.status) {
    case 0:
      return 'Sin conexión con el servicio (sin red). Verifica que los contenedores estén activos.';
    case 401:
      return detail ?? 'Sesión expirada o no autenticada (401 Unauthorized). Inicia sesión nuevamente.';
    case 403:
      return detail ?? 'Acceso denegado (403 Forbidden): no tienes permisos o el presupuesto está aprobado.';
    case 404:
      return detail ?? 'Recurso no encontrado (404 Not Found).';
    default:
      if (e?.status && e.status >= 500) {
        return detail ?? `Error interno del servidor (${e.status}). Intenta de nuevo más tarde.`;
      }
      return detail ?? (e?.status ? `Error HTTP ${e.status}` : fallback);
  }
}
