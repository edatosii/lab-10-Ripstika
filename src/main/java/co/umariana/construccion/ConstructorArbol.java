package co.umariana.construccion;

import co.umariana.modelo.ArbolGenealogico;
import co.umariana.modelo.ArbolInvalidoException;
import co.umariana.modelo.Persona;
import co.umariana.modelo.Posicion;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Construye un árbol genealógico a partir de un texto: cada línea es una persona.
 *
 *     posicion;nombre;nacimiento;fallecimiento
 *
 * Ejemplo (el fallecimiento puede quedar vacío):
 *
 *     1;John Doe;2002;
 *     2;Henry Doe;1920;1990
 *     3;Maria Doe;1921;
 *     1.1;Timmy Doe;2021;
 *     1.2;Amy Doe;2026;
 *
 * INSTRUCCIONES PARA EL ESTUDIANTE (Etapa 3)
 * ------------------------------------------
 * Reemplace el "throw new UnsupportedOperationException" por su implementación. Esta clase usa lo que ya
 * hizo en las etapas anteriores (Posicion, Persona y ArbolGenealogico). NO modifique las pruebas (src/test).
 *
 * REGLAS
 *   1. Si el texto es null o no tiene ninguna línea con contenido: ArbolInvalidoException (mensaje: "El texto está vacío...").
 *   2. Las líneas pueden terminar en \n o en \r\n. Las líneas en blanco se ignoran, pero los números de línea
 *      que se informan cuentan TODAS las líneas del texto (la primera es la línea 1).
 *   3. Cada línea tiene 4 campos separados por ';' (el cuarto, el fallecimiento, puede estar vacío). Se acepta
 *      también la línea con solo 3 campos y sin el último ';'. Los campos se recortan (trim).
 *      Si hay menos de 3 o más de 4 campos: error.
 *   4. El nacimiento y el fallecimiento deben ser números enteros (el fallecimiento vacío es "no tiene").
 *   5. Cada persona debe ser válida (Persona.validar) y no puede haber dos líneas con la misma posición.
 *   6. Debe existir la persona "1" (el sujeto), pero puede estar en cualquier línea.
 *   7. Las líneas pueden venir en CUALQUIER orden. El constructor debe agregarlas al árbol en un orden en el
 *      que cada persona ya tenga a la que necesita (por ejemplo, primero los ascendientes por número Ahnentafel
 *      ascendente y los descendientes por generación). Las reglas del árbol (ver ArbolGenealogico) las valida el árbol.
 *   8. Todos los errores asociados a una línea se informan con ArbolInvalidoException y un mensaje que EMPIEZA con
 *      "Línea N: " (N es el número de línea de la persona con el problema), por ejemplo:
 *          "Línea 3: la posición 2 está repetida."
 *      Los errores que no son de una línea (texto vacío, falta el sujeto) no llevan ese prefijo.
 *   9. Solo se muestran los miembros que están en el texto: no se crean personas vacías para los que faltan.
 */
public final class ConstructorArbol {

    private record Entrada(Persona persona, int numeroLinea) {
    }

    private ConstructorArbol() {
    }

    /**
     * Convierte el texto en un árbol (ver las reglas arriba).
     *
     * @throws ArbolInvalidoException si el texto no cumple las reglas
     */
    public static ArbolGenealogico construir(String texto) {
        // Un texto sin contenido no permite identificar al sujeto ni crear el árbol.
        if (texto == null || texto.isBlank()) {
            throw new ArbolInvalidoException("El texto está vacío.");
        }

        List<Entrada> entradas = new ArrayList<>();
        // Se conserva el número original de cada línea para asociar errores al texto de entrada.
        String[] lineas = texto.split("\\r?\\n", -1);
        for (int i = 0; i < lineas.length; i++) {
            String linea = lineas[i];
            if (linea.isBlank()) {
                continue;
            }
            String[] campos = linea.split(";", -1);
            // El límite -1 conserva el campo vacío del fallecimiento al final de la línea.
            if (campos.length < 3 || campos.length > 4) {
                throw errorLinea(i + 1, "se esperaban tres o cuatro campos.");
            }
            String posicion = campos[0].trim();
            String nombre = campos[1].trim();
            String nacimientoTexto = campos[2].trim();
            String fallecimientoTexto = campos.length == 4 ? campos[3].trim() : "";

            int nacimiento;
            Integer fallecimiento;
            // Se convierten los años aquí para reportar errores con el número de línea preciso.
            try {
                nacimiento = Integer.parseInt(nacimientoTexto);
            } catch (NumberFormatException e) {
                throw errorLinea(i + 1, "el año de nacimiento debe ser un número entero.");
            }
            if (fallecimientoTexto.isEmpty()) {
                fallecimiento = null;
            } else {
                try {
                    fallecimiento = Integer.valueOf(fallecimientoTexto);
                } catch (NumberFormatException e) {
                    throw errorLinea(i + 1, "el año de fallecimiento debe ser un número entero.");
                }
            }

            Persona persona = new Persona(posicion, nombre, nacimiento, fallecimiento);
            // La validación centralizada aplica las mismas reglas a texto, árbol y operaciones posteriores.
            try {
                persona.validar();
            } catch (IllegalArgumentException e) {
                throw errorLinea(i + 1, e.getMessage());
            }
            for (Entrada entrada : entradas) {
                // Detectar duplicados antes de ordenar permite señalar la línea repetida original.
                if (entrada.persona().getPosicion().equals(posicion)) {
                    throw errorLinea(i + 1, "la posición " + posicion + " está repetida.");
                }
            }
            entradas.add(new Entrada(persona, i + 1));
        }

        if (entradas.isEmpty()) {
            throw new ArbolInvalidoException("El texto está vacío.");
        }
        Entrada entradaSujeto = null;
        // El sujeto puede aparecer en cualquier línea, por eso se busca antes de agregar familiares.
        for (Entrada entrada : entradas) {
            if (Posicion.esSujeto(entrada.persona().getPosicion())) {
                entradaSujeto = entrada;
                break;
            }
        }
        if (entradaSujeto == null) {
            throw new ArbolInvalidoException("Falta la persona de la posición 1 (el sujeto).");
        }

        ArbolGenealogico arbol = new ArbolGenealogico(entradaSujeto.persona());
        entradas.remove(entradaSujeto);
        // Se agregan primero ascendientes de menor a mayor número y luego descendientes por generación.
        entradas.sort(Comparator
                .comparingInt((Entrada entrada) -> Posicion.esAscendiente(entrada.persona().getPosicion()) ? 0 : 1)
                .thenComparingInt(entrada -> Posicion.esAscendiente(entrada.persona().getPosicion())
                        ? Integer.parseInt(entrada.persona().getPosicion())
                        : Posicion.generacion(entrada.persona().getPosicion())));

        for (Entrada entrada : entradas) {
            // Las reglas de parentesco las verifica el árbol y aquí se adjuntan a su línea de origen.
            try {
                arbol.agregar(entrada.persona());
            } catch (ArbolInvalidoException e) {
                throw errorLinea(entrada.numeroLinea(), e.getMessage());
            }
        }
        return arbol;
    }

    private static ArbolInvalidoException errorLinea(int numeroLinea, String mensaje) {
        // Todos los errores atribuibles a una fila comparten el formato exigido por la interfaz.
        return new ArbolInvalidoException("Línea " + numeroLinea + ": " + mensaje);
    }
}
