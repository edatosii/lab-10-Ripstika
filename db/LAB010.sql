create schema lab10;
use lab10;
CREATE TABLE IF NOT EXISTS estudiantes (
    id       INT AUTO_INCREMENT PRIMARY KEY,
    nombre   VARCHAR(100) NOT NULL,
    correo   VARCHAR(100) NOT NULL UNIQUE,
    semestre INT          NOT NULL
);       