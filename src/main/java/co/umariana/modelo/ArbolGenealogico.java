package co.umariana.modelo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * El árbol genealógico: un SUJETO (posición "1") con sus ASCENDIENTES (padres, abuelos... en Ahnentafel)
 * y sus DESCENDIENTES (hijos, nietos... en d'Aboville). Ver la clase Posicion.
 *
 * Son en realidad dos árboles unidos en la raíz:
 *
 *     ascendientes:  árbol BINARIO. Cada persona tiene como mucho un padre (2n) y una madre (2n + 1).
 *     descendientes: árbol GENERAL. Cada persona tiene una lista de hijos (1.1, 1.2, ...) ordenada por número.
 *
 * El árbol solo guarda a los miembros que EXISTEN: no hay "huecos" ni personas vacías. Puede faltar el padre
 * de alguien, o el hijo 1.1 aunque exista el 1.2.
 *
 * INSTRUCCIONES PARA EL ESTUDIANTE (Etapa 2)
 * ------------------------------------------
 * 1. Implemente el árbol con sus PROPIOS NODOS: una clase interna "Nodo" con la Persona, el nodo del
 *    padre, el nodo de la madre y la lista de hijos. NO se permite usar Map ni Set (las pruebas lo
 *    revisan): para buscar una posición recorra el árbol siguiendo su camino
 *      - Ahnentafel: los bits de n después del primer 1 dicen el camino (0 = padre, 1 = madre).
 *        Ej.: 5 = 101 -> desde el sujeto: padre (0), luego madre (1).
 *      - D'Aboville: cada número de la posición dice a qué hijo bajar. Ej.: 1.2.3 -> hijo 2, luego su hijo 3.
 *    Se permite usar List/ArrayList para la lista de hijos.
 * 2. Todos los errores de las reglas se informan con {@link ArbolInvalidoException} (un mensaje claro).
 *    Los datos inválidos de una Persona los detecta Persona.validar() (IllegalArgumentException).
 * 3. NO modifique las pruebas (src/test).
 *
 * REGLAS DEL ÁRBOL
 *   - La posición "1" (el sujeto) existe siempre y no se puede eliminar.
 *   - Una posición no puede repetirse.
 *   - Un ascendiente n solo se puede agregar si ya existe su hijo n / 2, y debe haber NACIDO ANTES
 *     (año de nacimiento menor) que ese hijo.
 *   - Un descendiente solo se puede agregar si ya existe su progenitor, y debe haber NACIDO DESPUÉS
 *     (año de nacimiento mayor) que su progenitor.
 */
public class ArbolGenealogico {

    private static final class Nodo {
        private final Persona persona;
        // En Ahnentafel se navega por enlaces binarios; en d'Aboville, por la lista de hijos.
        private Nodo padre;
        private Nodo madre;
        private final List<Nodo> hijos = new ArrayList<>();

        private Nodo(Persona persona) {
            // Cada nodo conserva la persona y comienza sin familiares enlazados.
            this.persona = persona;
        }
    }

    private final Nodo raiz;
    private int cantidad;

    /**
     * Crea un árbol que solo tiene al sujeto.
     *
     * @throws IllegalArgumentException si la persona no es válida (Persona.validar)
     * @throws ArbolInvalidoException si su posición no es "1"
     */
    public ArbolGenealogico(Persona sujeto) {
        // El árbol solo puede nacer a partir de una persona válida ubicada en la posición raíz.
        if (sujeto == null) {
            throw new IllegalArgumentException("El sujeto es obligatorio.");
        }
        sujeto.validar();
        if (!Posicion.esSujeto(sujeto.getPosicion())) {
            throw new ArbolInvalidoException("El sujeto debe estar en la posición 1.");
        }
        raiz = new Nodo(sujeto);
        cantidad = 1;
    }

    /** @return el sujeto (la persona de la posición "1") */
    public Persona sujeto() {
        return raiz.persona;
    }

    /** @return cuántos miembros tiene el árbol (el sujeto cuenta; un árbol nuevo tiene 1) */
    public int tamano() {
        return cantidad;
    }

    /**
     * @return la persona de esa posición, o null si no existe en el árbol o si la posición no es válida
     */
    public Persona buscar(String posicion) {
        Nodo nodo = buscarNodo(posicion);
        return nodo == null ? null : nodo.persona;
    }

    /** @return true si hay alguien en esa posición (false también si la posición no es válida) */
    public boolean contiene(String posicion) {
        return buscarNodo(posicion) != null;
    }

    /**
     * Agrega una persona en la posición que ella misma trae (la usa el constructor del árbol a partir del texto).
     * El orden de llamada importa: primero debe existir la persona de la que depende (ver REGLAS DEL ÁRBOL).
     *
     * @return la misma persona
     * @throws IllegalArgumentException si la persona no es válida (Persona.validar)
     * @throws ArbolInvalidoException si la posición ya existe (o es "1"), si falta la persona de la que depende,
     *         o si los años de nacimiento no cumplen las reglas
     */
    public Persona agregar(Persona persona) {
        // Validar antes de enlazar evita dejar el árbol parcialmente modificado.
        if (persona == null) {
            throw new IllegalArgumentException("La persona es obligatoria.");
        }
        persona.validar();
        String posicion = persona.getPosicion();
        if (Posicion.esSujeto(posicion) || buscarNodo(posicion) != null) {
            throw new ArbolInvalidoException("La posición " + posicion + " ya existe.");
        }

        Nodo nuevo = new Nodo(persona);
        if (Posicion.esAscendiente(posicion)) {
            // Un ascendiente se conecta a su hijo en la línea y debe ser de una generación anterior.
            String posicionHijo = Posicion.posicionDelHijoEnLinea(posicion);
            Nodo hijo = buscarNodo(posicionHijo);
            if (hijo == null) {
                throw new ArbolInvalidoException("No existe el hijo en la línea de " + posicion + ".");
            }
            if (persona.getNacimiento() >= hijo.persona.getNacimiento()) {
                throw new ArbolInvalidoException("El ascendiente debe haber nacido antes que su hijo.");
            }
            if (Integer.parseInt(posicion) % 2 == 0) {
                hijo.padre = nuevo;
            } else {
                hijo.madre = nuevo;
            }
        } else {
            // Un descendiente se conecta a su progenitor y debe ser de una generación posterior.
            String posicionProgenitor = Posicion.posicionDelProgenitor(posicion);
            Nodo progenitor = buscarNodo(posicionProgenitor);
            if (progenitor == null) {
                throw new ArbolInvalidoException("No existe el progenitor de " + posicion + ".");
            }
            if (persona.getNacimiento() <= progenitor.persona.getNacimiento()) {
                throw new ArbolInvalidoException("El descendiente debe haber nacido después que su progenitor.");
            }
            insertarHijoOrdenado(progenitor, nuevo);
        }
        cantidad++;
        return persona;
    }

    /**
     * Agrega el PADRE de la persona de esa posición, en la posición 2n (ver Posicion.posicionDelPadre).
     * Solo se pueden agregar padres al sujeto y a sus ascendientes.
     *
     * @return la persona creada
     * @throws ArbolInvalidoException si la posición no existe, si no es el sujeto ni un ascendiente, si ese padre
     *         ya existe o si no cumple las reglas del árbol
     * @throws IllegalArgumentException si los datos de la persona no son válidos
     */
    public Persona agregarPadre(String posicion, String nombre, int nacimiento, Integer fallecimiento) {
        // Se restringen los padres a la rama de ascendientes antes de calcular la posición destino.
        Nodo nodo = nodoParaAgregarProgenitor(posicion);
        return agregar(new Persona(Posicion.posicionDelPadre(nodo.persona.getPosicion()),
                nombre, nacimiento, fallecimiento));
    }

    /**
     * Igual que agregarPadre, pero agrega la MADRE (posición 2n + 1).
     */
    public Persona agregarMadre(String posicion, String nombre, int nacimiento, Integer fallecimiento) {
        // La validación y el enlace se comparten con agregarPadre mediante agregar().
        Nodo nodo = nodoParaAgregarProgenitor(posicion);
        return agregar(new Persona(Posicion.posicionDeLaMadre(nodo.persona.getPosicion()),
                nombre, nacimiento, fallecimiento));
    }

    /**
     * Agrega un HIJO a la persona de esa posición. Solo se pueden agregar hijos al sujeto y a sus descendientes.
     * Su posición es la del progenitor + "." + el número siguiente al MAYOR número de hijo que ya exista
     * (o 1 si todavía no tiene hijos). Ej.: si "1" tiene los hijos 1.1 y 1.3, el nuevo es 1.4.
     *
     * @return la persona creada
     * @throws ArbolInvalidoException si la posición no existe, si no es el sujeto ni un descendiente, o si no cumple
     *         las reglas del árbol
     * @throws IllegalArgumentException si los datos de la persona no son válidos
     */
    public Persona agregarHijo(String posicion, String nombre, int nacimiento, Integer fallecimiento) {
        // El hijo nuevo recibe el índice posterior al mayor, sin renumerar hermanos existentes.
        Nodo progenitor = buscarNodo(posicion);
        if (progenitor == null || !(Posicion.esSujeto(posicion) || Posicion.esDescendiente(posicion))) {
            throw new ArbolInvalidoException("La posición debe ser el sujeto o un descendiente existente.");
        }
        int mayorOrden = 0;
        for (Nodo hijo : progenitor.hijos) {
            mayorOrden = Math.max(mayorOrden, Posicion.orden(hijo.persona.getPosicion()));
        }
        if (mayorOrden >= Posicion.MAX_ORDEN) {
            throw new ArbolInvalidoException("No se puede agregar otro hijo en esta posición.");
        }
        String nuevaPosicion = posicion + "." + (mayorOrden + 1);
        if (nuevaPosicion.length() > Posicion.MAX_LONGITUD) {
            throw new ArbolInvalidoException("La posición del nuevo hijo supera la longitud permitida.");
        }
        return agregar(new Persona(nuevaPosicion, nombre, nacimiento, fallecimiento));
    }

    /**
     * Elimina a la persona de esa posición y TODO lo que depende de ella:
     *   - si es un ascendiente: ella y todos sus propios ascendientes (sus padres, abuelos...);
     *   - si es un descendiente: ella y todos sus propios descendientes (hijos, nietos...).
     * Las demás posiciones NO cambian de número.
     *
     * @return cuántas personas se eliminaron (ella incluida)
     * @throws ArbolInvalidoException si la posición es "1" (el sujeto no se puede eliminar) o si no existe
     */
    public int eliminar(String posicion) {
        // Primero se desvincula el nodo de su familiar directo; las ramas dependientes se cuentan aparte.
        if (Posicion.esSujeto(posicion)) {
            throw new ArbolInvalidoException("El sujeto no se puede eliminar.");
        }
        Nodo nodo = buscarNodo(posicion);
        if (nodo == null) {
            throw new ArbolInvalidoException("No existe la posición " + posicion + ".");
        }
        int eliminados;
        if (Posicion.esAscendiente(posicion)) {
            Nodo hijo = buscarNodo(Posicion.posicionDelHijoEnLinea(posicion));
            if (Integer.parseInt(posicion) % 2 == 0) {
                hijo.padre = null;
            } else {
                hijo.madre = null;
            }
        } else {
            Nodo progenitor = buscarNodo(Posicion.posicionDelProgenitor(posicion));
            progenitor.hijos.remove(nodo);
        }
        eliminados = contarSubarbol(nodo);
        cantidad -= eliminados;
        return eliminados;
    }

    /**
     * @return todos los miembros en este orden: el sujeto; luego los ascendientes por número Ahnentafel
     *         ascendente (2, 3, 4, 5...); luego los descendientes en PREORDEN (cada persona y después sus hijos:
     *         1.1, 1.1.1, 1.1.2, 1.2, ...). La lista es una copia nueva: modificarla no cambia el árbol.
     *         Este orden garantiza que cada persona aparece DESPUÉS de la que necesita (útil para guardar y cargar).
     */
    public List<Persona> miembros() {
        // Este orden pone cada dependencia antes que sus dependientes para facilitar su reconstrucción.
        List<Persona> resultado = new ArrayList<>();
        resultado.add(raiz.persona);
        resultado.addAll(ascendientes());
        agregarDescendientesEnPreorden(raiz, resultado);
        return resultado;
    }

    /** @return solo los ascendientes, por número Ahnentafel ascendente (sin el sujeto) */
    public List<Persona> ascendientes() {
        // El recorrido por enlaces encuentra los miembros; luego se ordenan por su número Ahnentafel.
        List<Persona> resultado = new ArrayList<>();
        recolectarAscendientes(raiz, resultado);
        resultado.sort(Comparator.comparingInt(persona -> Integer.parseInt(persona.getPosicion())));
        return resultado;
    }

    /** @return solo los descendientes, en preorden (sin el sujeto) */
    public List<Persona> descendientes() {
        // El recorrido en preorden conserva el orden de hermanos y sus subárboles.
        List<Persona> resultado = new ArrayList<>();
        agregarDescendientesEnPreorden(raiz, resultado);
        return resultado;
    }

    private Nodo buscarNodo(String posicion) {
        // Las posiciones inválidas o los miembros ausentes se representan como una búsqueda sin resultado.
        if (!Posicion.esValida(posicion)) {
            return null;
        }
        if (Posicion.esAhnentafel(posicion)) {
            // Los bits posteriores al primer 1 codifican padre (0) o madre (1) desde la raíz.
            int numero = Integer.parseInt(posicion);
            Nodo actual = raiz;
            for (int bit = Integer.highestOneBit(numero) >> 1; bit > 0 && actual != null; bit >>= 1) {
                actual = (numero & bit) == 0 ? actual.padre : actual.madre;
            }
            return actual;
        }
        // En d'Aboville se sigue cada índice de hijo de izquierda a derecha por la ruta.
        Nodo actual = raiz;
        for (String parte : posicion.substring(2).split("\\.")) {
            int orden = Integer.parseInt(parte);
            Nodo siguiente = null;
            for (Nodo hijo : actual.hijos) {
                if (Posicion.orden(hijo.persona.getPosicion()) == orden) {
                    siguiente = hijo;
                    break;
                }
            }
            actual = siguiente;
            if (actual == null) {
                return null;
            }
        }
        return actual;
    }

    private Nodo nodoParaAgregarProgenitor(String posicion) {
        // Los progenitores solo se agregan en la rama Ahnentafel, nunca a descendientes.
        if (!(Posicion.esSujeto(posicion) || Posicion.esAscendiente(posicion))) {
            throw new ArbolInvalidoException("La posición debe ser el sujeto o un ascendiente.");
        }
        Nodo nodo = buscarNodo(posicion);
        if (nodo == null) {
            throw new ArbolInvalidoException("No existe la posición " + posicion + ".");
        }
        return nodo;
    }

    private void insertarHijoOrdenado(Nodo progenitor, Nodo nuevo) {
        // Mantiene la lista de hijos en orden numérico aun si las posiciones se agregan desordenadas.
        int ordenNuevo = Posicion.orden(nuevo.persona.getPosicion());
        int indice = 0;
        while (indice < progenitor.hijos.size()
                && Posicion.orden(progenitor.hijos.get(indice).persona.getPosicion()) < ordenNuevo) {
            indice++;
        }
        progenitor.hijos.add(indice, nuevo);
    }

    private int contarSubarbol(Nodo nodo) {
        // Cuenta el nodo y todos sus familiares dependientes para actualizar el tamaño al eliminar una rama.
        if (nodo == null) {
            return 0;
        }
        int total = 1 + contarSubarbol(nodo.padre) + contarSubarbol(nodo.madre);
        for (Nodo hijo : nodo.hijos) {
            total += contarSubarbol(hijo);
        }
        return total;
    }

    private void recolectarAscendientes(Nodo nodo, List<Persona> resultado) {
        // Se recorren ambas ramas parentales; el orden final se determina por el valor de posición.
        if (nodo.padre != null) {
            resultado.add(nodo.padre.persona);
            recolectarAscendientes(nodo.padre, resultado);
        }
        if (nodo.madre != null) {
            resultado.add(nodo.madre.persona);
            recolectarAscendientes(nodo.madre, resultado);
        }
    }

    private void agregarDescendientesEnPreorden(Nodo nodo, List<Persona> resultado) {
        // Se agrega primero cada hijo y luego su descendencia para producir el recorrido preorden.
        for (Nodo hijo : nodo.hijos) {
            resultado.add(hijo.persona);
            agregarDescendientesEnPreorden(hijo, resultado);
        }
    }
}
