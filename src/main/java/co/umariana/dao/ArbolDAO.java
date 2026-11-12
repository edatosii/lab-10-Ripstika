package co.umariana.dao;

import co.umariana.conexion.ConexionBD;
import co.umariana.modelo.ArbolGenealogico;
import co.umariana.modelo.Persona;
import co.umariana.modelo.ResumenArbol;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO (Data Access Object): la única clase que habla con la base de datos. Guarda los árboles genealógicos
 * en las tablas "arboles" y "miembros" (ver db/schema.sql). Se construye igual que EstudianteDAO del laboratorio 9.
 *
 * INSTRUCCIONES PARA EL ESTUDIANTE (Etapa 4)
 * ------------------------------------------
 * 1. Cada método marcado con "TODO" lanza UnsupportedOperationException. Reemplace esa línea por su
 *    implementación con JDBC.
 * 2. NO modifique las pruebas (src/test). Las pruebas NO necesitan MySQL: usan una base de datos H2 en memoria.
 * 3. Esta etapa usa las anteriores: para guardar recorra arbol.miembros(); para cargar, reconstruya el árbol con
 *    new ArbolGenealogico(sujeto) y arbol.agregar(...).
 *
 * REGLAS (las pruebas las verifican)
 *  - Los métodos NO lanzan SQLException: la capturan, imprimen el mensaje con System.out.println y devuelven
 *    un valor "de error" (-1, false, lista vacía o null).
 *  - Use SIEMPRE PreparedStatement con parámetros (?), nunca concatene datos dentro del SQL.
 *  - Guardar un árbol son VARIAS sentencias (el árbol y todos sus miembros): hágalo en una TRANSACCIÓN
 *    (con.setAutoCommit(false); ... con.commit(); y con.rollback() si algo falla) para que nunca quede
 *    guardado "a medias". Antes de cerrar la conexión vuelva a dejar setAutoCommit(true).
 *  - El id de cada fila lo genera la base de datos (AUTO_INCREMENT).
 *  - Los miembros se guardan en el orden de arbol.miembros(): así, al leerlos ORDER BY id, cada persona
 *    aparece después de la que necesita para poder agregarla al árbol.
 */
public class ArbolDAO {

    // =====================================================================
    // ETAPA 4.1. Guardar un árbol nuevo (INSERT en dos tablas, con transacción)
    // =====================================================================

    /**
     * Guarda un árbol nuevo: una fila en "arboles" (con el nombre) y una fila en "miembros" por cada persona.
     *
     * @return el id que la base de datos le dio al árbol; -1 si el nombre es nulo, está en blanco o tiene más
     *         de 100 caracteres, si el árbol es null, o si hubo un error (en ese caso NO queda nada guardado)
     */
    public long guardar(String nombre, ArbolGenealogico arbol) {
        // Los datos incompletos se rechazan antes de abrir una conexión.
        if (nombre == null || nombre.isBlank() || nombre.length() > 100 || arbol == null) {
            return -1;
        }
        try (Connection con = ConexionBD.conectar()) {
            // Árbol y miembros se guardan juntos para evitar registros parciales.
            con.setAutoCommit(false);
            try {
                long id;
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO arboles (nombre) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
                    // La clave generada se usa para vincular todas las filas de miembros.
                    ps.setString(1, nombre);
                    ps.executeUpdate();
                    try (ResultSet claves = ps.getGeneratedKeys()) {
                        if (!claves.next()) {
                            throw new SQLException("La base de datos no devolvió el id del árbol.");
                        }
                        id = claves.getLong(1);
                    }
                }
                insertarMiembros(con, id, arbol);
                con.commit();
                return id;
            } catch (SQLException e) {
                // Ante cualquier error JDBC, se revierte también la fila de arboles.
                rollback(con);
                System.out.println(e.getMessage());
                return -1;
            } finally {
                // Se restaura el modo esperado antes de cerrar o reutilizar la conexión.
                restaurarAutoCommit(con);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return -1;
        }
    }

    // =====================================================================
    // ETAPA 4.2. Listar (SELECT con JOIN y COUNT)
    // =====================================================================

    /**
     * @return un resumen por cada árbol guardado (id, nombre, cuántos miembros tiene y cuándo se creó),
     *         ordenados por id; lista vacía si no hay árboles o si hay un error
     */
    public List<ResumenArbol> listar() {
        List<ResumenArbol> arboles = new ArrayList<>();
        // LEFT JOIN conserva árboles vacíos y COUNT calcula los miembros asociados.
        String sql = "SELECT a.id, a.nombre, a.creado, COUNT(m.id) AS total "
                + "FROM arboles a LEFT JOIN miembros m ON m.arbol_id = a.id "
                + "GROUP BY a.id, a.nombre, a.creado ORDER BY a.id";
        try (Connection con = ConexionBD.conectar();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                arboles.add(new ResumenArbol(rs.getLong("id"), rs.getString("nombre"),
                        rs.getInt("total"), rs.getTimestamp("creado").toLocalDateTime()));
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return new ArrayList<>();
        }
        return arboles;
    }

    // =====================================================================
    // ETAPA 4.3. Cargar un árbol (SELECT y reconstrucción del árbol)
    // =====================================================================

    /**
     * @return el árbol con ese id, reconstruido con todos sus miembros; null si no existe ese id o si hay un error
     */
    public ArbolGenealogico cargar(long id) {
        // El orden por id respeta el orden de inserción y las dependencias entre miembros.
        String sql = "SELECT posicion, nombre, nacimiento, fallecimiento FROM miembros "
                + "WHERE arbol_id = ? ORDER BY id";
        try (Connection con = ConexionBD.conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                ArbolGenealogico arbol = null;
                while (rs.next()) {
                    // getInt devuelve cero para SQL NULL; wasNull distingue ese caso de un año real.
                    int fallecimientoValor = rs.getInt("fallecimiento");
                    Integer fallecimiento = rs.wasNull() ? null : fallecimientoValor;
                    Persona persona = new Persona(
                            rs.getString("posicion"), rs.getString("nombre"),
                            rs.getInt("nacimiento"), fallecimiento);
                    if (arbol == null) {
                        // El primer registro es el sujeto; los siguientes ya encuentran su dependencia.
                        arbol = new ArbolGenealogico(persona);
                    } else {
                        arbol.agregar(persona);
                    }
                }
                return arbol;
            }
        } catch (SQLException | IllegalArgumentException e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

    // =====================================================================
    // ETAPA 4.4. Actualizar (reemplazar los miembros de un árbol, con transacción)
    // =====================================================================

    /**
     * Guarda los cambios de un árbol ya existente (por ejemplo, después de agregar o eliminar miembros):
     * deja en la base de datos exactamente los miembros de {@code arbol}. El nombre del árbol NO cambia.
     *
     * @return true si se actualizó; false si no existe un árbol con ese id, si el árbol es null o si hubo un
     *         error (en ese caso NO se modifica nada: ni se borran los miembros anteriores)
     */
    public boolean actualizar(long id, ArbolGenealogico arbol) {
        // Un árbol nulo no debe borrar los miembros que ya están almacenados.
        if (arbol == null) {
            return false;
        }
        try (Connection con = ConexionBD.conectar()) {
            // El borrado y la reinserción se confirman juntos para conservar los datos ante un fallo.
            con.setAutoCommit(false);
            try {
                try (PreparedStatement verificar = con.prepareStatement("SELECT id FROM arboles WHERE id = ?")) {
                    verificar.setLong(1, id);
                    try (ResultSet rs = verificar.executeQuery()) {
                        if (!rs.next()) {
                            // No hay nada que actualizar si el árbol no existe.
                            con.rollback();
                            return false;
                        }
                    }
                }
                try (PreparedStatement borrar = con.prepareStatement("DELETE FROM miembros WHERE arbol_id = ?")) {
                    // Solo se reemplazan miembros del árbol solicitado; el nombre permanece intacto.
                    borrar.setLong(1, id);
                    borrar.executeUpdate();
                }
                insertarMiembros(con, id, arbol);
                con.commit();
                return true;
            } catch (SQLException e) {
                rollback(con);
                System.out.println(e.getMessage());
                return false;
            } finally {
                restaurarAutoCommit(con);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    // =====================================================================
    // ETAPA 4.5. Eliminar (DELETE)
    // =====================================================================

    /**
     * Elimina el árbol y todos sus miembros (la base de datos borra los miembros sola: ON DELETE CASCADE).
     *
     * @return true si se eliminó; false si no existía ese id o si hubo un error
     */
    public boolean eliminar(long id) {
        // La clave foránea con ON DELETE CASCADE elimina también los miembros relacionados.
        try (Connection con = ConexionBD.conectar();
             PreparedStatement ps = con.prepareStatement("DELETE FROM arboles WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    private void insertarMiembros(Connection con, long id, ArbolGenealogico arbol) throws SQLException {
        // Reutiliza una sentencia parametrizada y preserva el orden necesario para reconstruir el árbol.
        String sql = "INSERT INTO miembros (arbol_id, posicion, nombre, nacimiento, fallecimiento) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (Persona persona : arbol.miembros()) {
                ps.setLong(1, id);
                ps.setString(2, persona.getPosicion());
                ps.setString(3, persona.getNombre());
                ps.setInt(4, persona.getNacimiento());
                if (persona.getFallecimiento() == null) {
                    // El valor opcional se guarda como SQL NULL, no como cero.
                    ps.setNull(5, Types.INTEGER);
                } else {
                    ps.setInt(5, persona.getFallecimiento());
                }
                ps.executeUpdate();
            }
        }
    }

    private void rollback(Connection con) {
        // El error de rollback también se informa; no se oculta el problema de la transacción.
        try {
            con.rollback();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    private void restaurarAutoCommit(Connection con) {
        // Evita dejar la conexión con transacciones manuales si se reutiliza más adelante.
        try {
            con.setAutoCommit(true);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }
}
