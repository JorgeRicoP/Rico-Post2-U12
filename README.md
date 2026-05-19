# Sistema de Gestión de Pedidos
### Unidad 12 — Post-Contenido 2 | Patrones de Diseño de Software | Ingeniería de Sistemas 2026

---

## Arquitectura del Sistema

El sistema implementa una **Arquitectura Hexagonal (Ports & Adapters)** con organización de paquetes feature-first. El principio central es que el dominio no conoce ni depende de ningún detalle de infraestructura o framework; todo lo externo se comunica con el dominio a través de puertos (interfaces).

```
com/empresa/pedidos/
├── dominio/                          # Núcleo — sin dependencias externas
│   ├── Pedido.java
│   ├── TipoPedido.java
│   ├── EstadoPedido.java
│   ├── eventos/
│   │   └── PedidoProcesadoEvent.java
│   └── puertos/                      # Contratos del dominio hacia afuera
│       ├── RepositorioPedidos.java
│       ├── ProcesadorPedido.java
│       └── ServicioNotificacion.java
├── aplicacion/                       # Orquestación — Factory
│   └── ProcesadorPedidoFactory.java
├── infraestructura/                  # Adaptadores técnicos
│   ├── persistencia/
│   │   └── RepositorioPedidosJpa.java
│   └── notificaciones/
│       ├── NotificacionEmail.java
│       └── NotificacionLog.java
└── adaptadores/                      # Interfaces externas
    ├── procesadores/                 # Implementaciones Strategy
    │   ├── ProcesadorPedidoEstandar.java
    │   ├── ProcesadorPedidoExpress.java
    │   └── ProcesadorPedidoInternacional.java
    ├── facade/
    │   └── FachadaPedidos.java
    └── rest/
        └── PedidoController.java
```

**Flujo de una solicitud:**

```
HTTP POST /api/pedidos
        ↓
PedidoController          (solo conoce FachadaPedidos)
        ↓
FachadaPedidos            (orquesta todo el flujo interno)
        ↓
ProcesadorPedidoFactory   (selecciona la Strategy por tipo)
        ↓
ProcesadorPedido[X]       (calcula costo y cambia estado)
        ↓
RepositorioPedidosJpa     (persiste en H2)
        ↓
ApplicationEventPublisher (publica PedidoProcesadoEvent)
        ↓
NotificacionEmail         (Observer — envía email)
NotificacionLog           (Observer — registra en log)
```

---

## Justificación de Cada Patrón

### 1. Strategy — `ProcesadorPedido`

**Problema que resuelve:**
El servicio legacy `ServicioPedidosLegacy` contenía una cadena de `if/else` que evaluaba el tipo de pedido para aplicar el cálculo de costo correspondiente. Cada vez que se agregara un nuevo tipo de pedido, habría que modificar el mismo método, violando el principio **Open/Closed** y elevando la complejidad ciclomática del servicio central.

**Cómo lo resuelve:**
Se define el puerto `ProcesadorPedido` con dos métodos: `getTipo()` y `procesar(Pedido)`. Cada tipo de pedido tiene su propia clase que implementa este puerto de forma independiente:

- `ProcesadorPedidoEstandar` → aplica recargo del 10%
- `ProcesadorPedidoExpress` → aplica recargo del 30%
- `ProcesadorPedidoInternacional` → aplica recargo del 50% + $25 fijos

Agregar un nuevo tipo de pedido requiere únicamente crear una nueva clase anotada con `@Component`, sin tocar ningún código existente.

---

### 2. Factory — `ProcesadorPedidoFactory`

**Problema que resuelve:**
Con el patrón Strategy se eliminaron los `if/else` de cálculo, pero surgió una nueva pregunta: ¿quién selecciona qué implementación de `ProcesadorPedido` usar según el tipo del pedido recibido? Sin una Factory, esa responsabilidad caería sobre la Facade, reintroduciendo condicionales.

**Cómo lo resuelve:**
Spring inyecta automáticamente todas las implementaciones de `ProcesadorPedido` registradas como `@Component` en una lista. La Factory las convierte en un `Map<TipoPedido, ProcesadorPedido>` en construcción, de modo que la selección en tiempo de ejecución es una simple búsqueda en el mapa (`O(1)`) sin ningún condicional. La Facade solo llama `factory.obtener(tipo)`.

---

### 3. Observer — Spring Events (`PedidoProcesadoEvent`)

**Problema que resuelve:**
En el servicio legacy, la notificación por email estaba acoplada directamente mediante `@Autowired JavaMailSender mail` dentro del mismo método de negocio. Esto significaba que el servicio tenía que conocer todos los canales de notificación existentes, y agregar un nuevo canal (por ejemplo, SMS o Slack) implicaba modificar el servicio principal.

**Cómo lo resuelve:**
La Facade publica un evento de dominio `PedidoProcesadoEvent` a través del `ApplicationEventPublisher` de Spring. Los listeners `NotificacionEmail` y `NotificacionLog` se suscriben al evento con `@EventListener` de forma completamente independiente entre sí y de la Facade. El dominio y la Facade no saben cuántos ni cuáles listeners existen. Agregar un nuevo canal de notificación es crear un nuevo `@Component` que implemente `ServicioNotificacion`.

---

### 4. Facade — `FachadaPedidos`

**Problema que resuelve:**
El controlador REST no debería conocer la existencia de la Factory, las implementaciones de Strategy, el repositorio JPA ni el mecanismo de publicación de eventos. Si el controlador dependiera directamente de todos estos componentes, cualquier cambio en la lógica interna impactaría también la capa REST.

**Cómo lo resuelve:**
`FachadaPedidos` expone únicamente dos métodos simples al mundo exterior: `crearPedido(Pedido)` y `buscarPorId(Long)`. Internamente orquesta la Factory, la Strategy, la persistencia y la publicación de eventos. El controlador `PedidoController` tiene una única dependencia: `FachadaPedidos`. Su complejidad ciclomática es 1 (sin ningún condicional en el flujo principal).

---

## Métricas de Calidad — Antes y Después

Las métricas se obtuvieron ejecutando SonarQube sobre el código legacy y sobre el código refactorizado.

| Métrica | Antes — `ServicioPedidosLegacy` | Después — `FachadaPedidos` |
|---|---|---|
| Cyclomatic Complexity (servicio principal) | **4** | **1** |
| Cognitive Complexity | **6** | **0** |
| Dependencia directa a `JavaMailSender` en capa de aplicación | ✅ Presente | ❌ Eliminada |
| Dependencia directa a `JpaRepository` en capa de aplicación | ✅ Presente | ❌ Eliminada |
| Cobertura de pruebas | **0%** | **>80%** |
| Quality Gate SonarQube | ❌ No configurado | ✅ **Passed** |
| Warnings de SonarQube | Sin análisis | **0** |

> Las capturas del análisis SonarQube se encuentran en la carpeta `capturas/`.

---

## Cómo Ejecutar el Proyecto

### Requisitos previos
- Java 17
- Maven 3.x
- Docker (para SonarQube)

### Compilar y ejecutar pruebas

```bash
mvn clean package
```

### Levantar la aplicación

```bash
mvn spring-boot:run
```

La API queda disponible en `http://localhost:8080`.

### Probar el endpoint con curl

```bash
# Pedido Estándar
curl -X POST http://localhost:8080/api/pedidos \
  -H "Content-Type: application/json" \
  -d '{"cliente":"Ana García","subtotal":100.0,"tipo":"ESTANDAR"}'

# Pedido Express
curl -X POST http://localhost:8080/api/pedidos \
  -H "Content-Type: application/json" \
  -d '{"cliente":"Carlos López","subtotal":100.0,"tipo":"EXPRESS"}'

# Pedido Internacional
curl -X POST http://localhost:8080/api/pedidos \
  -H "Content-Type: application/json" \
  -d '{"cliente":"María Torres","subtotal":100.0,"tipo":"INTERNACIONAL"}'
```

### Análisis con SonarQube

```bash
# 1. Levantar SonarQube
docker run -d -p 9000:9000 sonarqube:lts-community

# 2. Esperar ~1 minuto y abrir http://localhost:9000 (admin/admin)
# 3. Generar un token en My Account → Security → Generate Token

# 4. Ejecutar el análisis
mvn clean verify sonar:sonar \
  -Dsonar.projectKey=pedidos-integrado \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.login=sqp_64311d492db96f6bae2afb091122a157d4bfa2a5
```

---

## Pruebas Implementadas

| Prueba | Tipo | Patrón validado |
|---|---|---|
| `ProcesadorPedidoFactoryTest` | Unitaria | Factory |
| `ProcesadorPedidoStrategyTest` | Unitaria | Strategy |
| `ArchitectureTest` | Arquitectura (ArchUnit) | Hexagonal / desacoplamiento |
| `FachadaPedidosIntegrationTest` | Integración (`@SpringBootTest`) | Todos los patrones integrados |

---

## Validación Arquitectónica

Este proyecto incluye validación arquitectónica automática mediante **ArchUnit**, ejecutada en cada push a través de **GitHub Actions**.

### Las 5 reglas ArchUnit (`ReglasArquitectura.java`)

| # | Regla | Descripción |
|---|-------|-------------|
| 1 | `dominioAislado` | Las clases del paquete `dominio` no pueden depender de `infraestructura`, `adaptadores`, `javax.persistence` ni `org.springframework.mail`. |
| 2 | `controladorSoloFacade` | Las clases de `adaptadores.rest` solo pueden acceder a `adaptadores.facade`, `dominio` y librerías de Spring Web / Java. |
| 3 | `puertosComoInterfaces` | Todas las clases en `dominio.puertos` deben ser interfaces. |
| 4 | `procesadoresImplementanPuerto` | Todas las clases en `adaptadores.procesadores` deben implementar `ProcesadorPedido`. |
| 5 | `infraNoAccedeRest` | Las clases de `infraestructura` no pueden acceder a clases de `adaptadores.rest`. |

### Ejecutar validación localmente

```bash
mvn test -Dtest=ReglasArquitectura
```

### Decisiones de diseño (ADR)

Consulta la carpeta [`docs/adr/`](docs/adr/) para ver las decisiones de arquitectura documentadas:

- [ADR-001](docs/adr/ADR-001.md) — Arquitectura Hexagonal para aislar el dominio
- [ADR-002](docs/adr/ADR-002.md) — Factory + Strategy para selección de procesador
- [ADR-003](docs/adr/ADR-003.md) — Spring Events (Observer) para notificaciones