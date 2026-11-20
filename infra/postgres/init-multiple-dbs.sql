-- Script de inicializacion de bases de datos aisladas por microservicio
CREATE DATABASE insumo_db;
CREATE DATABASE presupuesto_db;

\connect insumo_db;

CREATE TABLE IF NOT EXISTS insumos (
    id UUID PRIMARY KEY,
    codigo VARCHAR(40) NOT NULL UNIQUE,
    descripcion VARCHAR(255) NOT NULL,
    unidad_medida VARCHAR(15) NOT NULL CHECK (unidad_medida IN ('M', 'M2', 'M3', 'KG', 'BULTO', 'UNIDAD', 'DIA')),
    precio_unitario NUMERIC(19, 3) NOT NULL CHECK (precio_unitario >= 0),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    updated_by VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS historial_precios_insumo (
    id UUID PRIMARY KEY,
    insumo_id UUID NOT NULL REFERENCES insumos(id),
    precio_anterior NUMERIC(19, 3),
    precio_nuevo NUMERIC(19, 3) NOT NULL,
    modificado_por VARCHAR(100),
    fecha_cambio TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

\connect presupuesto_db;

CREATE TABLE IF NOT EXISTS presupuestos (
    id UUID PRIMARY KEY,
    codigo VARCHAR(40) NOT NULL UNIQUE,
    nombre VARCHAR(200) NOT NULL,
    cliente VARCHAR(150) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'BORRADOR' CHECK (estado IN ('BORRADOR', 'EN_REVISION', 'APROBADO')),
    porcentaje_administracion NUMERIC(7, 4) NOT NULL DEFAULT 8.0000,
    porcentaje_imprevistos NUMERIC(7, 4) NOT NULL DEFAULT 3.0000,
    porcentaje_utilidad NUMERIC(7, 4) NOT NULL DEFAULT 5.0000,
    costo_fijo NUMERIC(19, 2) DEFAULT 0.00,
    precio_venta_unitario NUMERIC(19, 2),
    costo_variable_unitario NUMERIC(19, 2),
    presupuesto_base_id UUID REFERENCES presupuestos(id) ON DELETE SET NULL,
    creado_por VARCHAR(100) NOT NULL,
    fecha_creacion TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    enviado_revision_por VARCHAR(100),
    fecha_envio_revision TIMESTAMP WITH TIME ZONE,
    aprobado_por VARCHAR(100),
    fecha_aprobacion TIMESTAMP WITH TIME ZONE,
    motivo_rechazo TEXT,
    rechazado_por VARCHAR(100),
    fecha_rechazo TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS capitulos (
    id UUID PRIMARY KEY,
    presupuesto_id UUID NOT NULL REFERENCES presupuestos(id) ON DELETE CASCADE,
    orden INTEGER NOT NULL,
    codigo VARCHAR(40) NOT NULL,
    nombre VARCHAR(150) NOT NULL
);

CREATE TABLE IF NOT EXISTS items_presupuesto (
    id UUID PRIMARY KEY,
    capitulo_id UUID NOT NULL REFERENCES capitulos(id) ON DELETE CASCADE,
    orden INTEGER NOT NULL,
    insumo_id UUID NOT NULL,
    codigo_insumo VARCHAR(40) NOT NULL,
    descripcion VARCHAR(255) NOT NULL,
    unidad_medida VARCHAR(15) NOT NULL,
    cantidad NUMERIC(19, 3) NOT NULL CHECK (cantidad > 0),
    precio_unitario_copia NUMERIC(19, 3) NOT NULL CHECK (precio_unitario_copia >= 0)
);

CREATE TABLE IF NOT EXISTS versiones_presupuesto (
    id UUID PRIMARY KEY,
    presupuesto_id UUID NOT NULL REFERENCES presupuestos(id) ON DELETE CASCADE,
    numero_version INTEGER NOT NULL,
    etiqueta VARCHAR(120) NOT NULL,
    comentario_envio VARCHAR(500),
    estado_en_momento VARCHAR(20) NOT NULL,
    creado_por VARCHAR(100) NOT NULL,
    fecha_creacion TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    codigo_presupuesto VARCHAR(40) NOT NULL,
    nombre_presupuesto VARCHAR(200) NOT NULL,
    destinatario_sub VARCHAR(100),
    fecha_notificacion TIMESTAMP WITH TIME ZONE,
    costo_directo NUMERIC(19, 2) NOT NULL,
    valor_administracion NUMERIC(19, 2) NOT NULL,
    valor_imprevistos NUMERIC(19, 2) NOT NULL,
    valor_utilidad NUMERIC(19, 2) NOT NULL,
    precio_venta NUMERIC(19, 2) NOT NULL,
    snapshot_json TEXT NOT NULL,
    CONSTRAINT uq_version_presupuesto UNIQUE (presupuesto_id, numero_version)
);

CREATE TABLE IF NOT EXISTS desembolsos_capitulo (
    id UUID PRIMARY KEY,
    presupuesto_id UUID NOT NULL REFERENCES presupuestos(id) ON DELETE CASCADE,
    capitulo_id UUID NOT NULL REFERENCES capitulos(id) ON DELETE CASCADE,
    semana INTEGER NOT NULL CHECK (semana >= 1),
    porcentaje_avance NUMERIC(7, 4) NOT NULL CHECK (porcentaje_avance >= 0 AND porcentaje_avance <= 100),
    CONSTRAINT uq_desembolso_cap_semana UNIQUE (capitulo_id, semana)
);
