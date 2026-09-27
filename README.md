# FuelRadar

App Android nativa en español para comparar gasolineras cercanas. Kotlin, Jetpack Compose y Material 3. Compatible con **Android 8.0 o posterior**.

## Novedades de la versión 1.1

- Diseño oscuro con degradados azul, turquesa y verde lima, tarjetas con contraste y navegación inferior: **Radar, Ahorro, Mis vales y Ajustes**.
- Botones que responden al pulsar, transiciones entre menús y la mascota original **Chispa**, que saluda al tocarla. Las animaciones son breves y respetan la escala de animación del sistema; no hay animación continua en segundo plano.
- Guía de programas **Waylet, Moeve gow, miBP, Mundo Galp y El Club Carrefour**, favoritos guardados en el móvil, enlaces oficiales y pasos para obtener sus ventajas.
- Estaciones cercanas de cada marca para el carburante y radio elegidos. La coincidencia usa el rótulo oficial: **no garantiza la adhesión de esa estación ni la elegibilidad de tu cuenta**.
- **Mis vales** guarda manualmente nombre, programa, código opcional y caducidad de vales que ya tengas. Permite copiar el código, marcarlo usado o eliminar la anotación. No crea, activa ni canjea cupones, ni importa cuentas de otras apps. No genera QR que puedan confundirse con vales oficiales.
- Calculadora por estación: litros, descuento inmediato en céntimos/litro, vale fijo en euros o saldo futuro. Solo simula una ventaja cada vez, sin combinar campañas. El saldo futuro aparece separado y **no reduce el pago de hoy**. No se modifica el orden por precio oficial del Radar.

**Al actualizar desde la primera versión 1.0:** su clave debug no se conservó en Actions, por lo que Android no permite instalar 1.1 encima. Es necesario desinstalar 1.0 e instalar este APK; se perderán el histórico y los ajustes locales. Desde 1.1 el workflow genera y conserva la clave en una ruta explícita. La app solicita ubicación solo al usar GPS; no hace falta una cuenta FuelRadar.

### Descuentos: alcance y fuentes

Guía revisada el **27/09/2026**. No se fijan importes publicitarios en la app: las campañas, fechas, tipos de gasolina, mínimos, límites y requisitos cambian. Después de 30 días la guía muestra un aviso para volver a comprobar las condiciones. Los precios oficiales se actualizan automáticamente; **el catálogo orientativo de programas no es un servicio de promociones en tiempo real**.

Fuentes oficiales usadas:

- [Waylet / Repsol](https://www.repsol.es/particulares/soluciones-energeticas/coche/): saldo y cupones; pago e identificación según las condiciones.
- [Moeve gow](https://www.moeve.es/es/particular/club-gow): saldo, campañas y consulta de condiciones.
- [miBP](https://mibp.es/): Ahorro miBP y promociones que pueden exigir activación o limitarse por zona.
- [Mundo Galp](https://www.galp.com/es/mundo-galp): promociones de la cuenta y estaciones adheridas indicadas en cada cupón.
- [El Club Carrefour](https://www.carrefour.es/CLUBCARREFOUR/partners/gasolineras-carrefour/): acumulación en ChequeAhorro para compras posteriores según las condiciones, distinta de una rebaja inmediata en carburante.

FuelRadar abre estos sitios en el navegador o en la aplicación que Android tenga asociada al enlace. El alta, los datos personales, la activación, el pago y el canje los gestiona cada proveedor. No hay API privada integrada, afiliación ni acuerdo comercial con estas marcas. Para emitir o recuperar automáticamente cupones personales haría falta una integración autorizada por el proveedor; nunca se promete un vale antes de que el emisor lo conceda.

Los vales locales no se envían a un servidor y se excluyen de las copias y transferencias Android configurables por la app. Desinstalar o borrar los datos de FuelRadar elimina las anotaciones. No guardes tarjetas bancarias ni contraseñas en el campo de código.

### Mascota

Chispa es una mascota original generada con la herramienta integrada de imágenes y se incluye en `app/src/main/res/drawable-nodpi/mascot_chispa.png`, con fondo transparente. El prompt de creación está en [docs/mascot-prompt.txt](docs/mascot-prompt.txt).

## Descargar e instalar el APK

1. Inicia sesión en GitHub y entra en [Actions → Android APK](https://github.com/Segado2/FuelRadar-Android/actions/workflows/android.yml).
2. Abre la ejecución más reciente que tenga una marca verde. Cada cambio en `main` inicia una compilación. También puedes pulsar **Run workflow → Run workflow** para generar otra.
3. Baja hasta **Artifacts** y descarga **FuelRadar-debug**. GitHub entrega un ZIP: descomprímelo y abre **app-debug.apk** en el móvil.
4. Si Android lo solicita, permite a tu navegador o gestor de archivos **instalar aplicaciones desconocidas**. Instala y abre **FuelRadar**.
5. Pulsa **Usar mi ubicación** y permite ubicación durante el uso. Puedes conceder ubicación aproximada, aunque la precisa mejora las distancias. También puedes buscar un municipio.
6. Activa **Avisos de precios** si quieres notificaciones y acepta el permiso de Android.

Los artifacts duran 30 días. Si caducan, vuelve a ejecutar el workflow. El APK es una compilación debug instalable, no una publicación en Google Play. Actions conserva su clave debug en caché para facilitar actualizaciones; si esa caché se pierde, Android puede exigir desinstalar la versión anterior, lo que elimina sus datos locales. Para distribución estable debe configurarse una firma release privada y aumentar `versionCode`.

## Funciones

- GPS y ubicación por red mientras se solicita en primer plano; manejo de permiso denegado y tiempo de espera. No necesita Google Play Services.
- Alternativa por municipio, usando el centro aproximado de las estaciones del municipio; no sustituye una posición GPS exacta.
- Gasolina 95 **E5** y Diésel **Gasóleo A**, sin mezclar E10, premium o gasóleo agrícola.
- Radios **10 / 25 / 50 km**, orden por precio o distancia y desempate por distancia/precio.
- Precio con tres decimales, dirección, municipio y horario comunicado. Solo estaciones de venta al público y con precio válido del carburante elegido.
- Navegación en Google Maps y alternativa web si no está instalado. Las distancias de la lista son en línea recta, no por carretera.
- Caché local para consultar lo descargado sin conexión. Fecha de los precios y de la última consulta, aviso de datos con más de 24 horas y actualización manual.
- Histórico SQLite de **90 días**, desde la primera descarga: cada cambio observado y una referencia diaria, con gráfico y detalle. No inventa precios anteriores a la instalación ni registra cambios ocurridos entre consultas. Las flechas muestran el último cambio observado y su fecha.
- Actualización mediante WorkManager **aproximadamente cada 6 horas**, con conexión a Internet y reintentos con espera creciente. Android puede retrasarla por batería, Doze o restricciones del fabricante. Tras forzar la detención, vuelve a abrir la app.
- Avisos opcionales por subidas o bajadas de **al menos 0,010 €/l**, para el carburante y radio elegidos alrededor de la **última referencia guardada**. La primera descarga y los precios ausentes no generan falsas alertas. También se notifican cambios relevantes detectados al actualizar manualmente.

## Fuente oficial y privacidad

Se consulta por HTTPS el [servicio REST oficial de carburantes](https://sedeaplicaciones.minetur.gob.es/ServiciosRESTCarburantes/PreciosCarburantes/EstacionesTerrestres/), cuya [documentación](https://sedeaplicaciones.minetur.gob.es/ServiciosRESTCarburantes/PreciosCarburantes/help) publica el contrato. Fuente: Ministerio para la Transición Ecológica y el Reto Demográfico / Geoportal de gasolineras. No hace falta clave API.

Se interpretan las comas decimales y la fecha de origen en la zona horaria de Madrid. Una respuesta vacía, fallida o más antigua no sustituye los últimos datos válidos. No se deduce si una estación está abierta a partir del texto libre del horario. Confirma precios y disponibilidad en la estación.

No hay cuenta, anuncios, analítica ni servidor propio. La ubicación y el histórico permanecen en este dispositivo, con copia de seguridad Android desactivada. La descarga nacional no envía tus coordenadas al servicio de precios. Al pulsar **Cómo llegar**, se abre Maps con el destino. La app no solicita ubicación en segundo plano; abre la app y actualiza GPS si cambias de zona.

## Compilar y verificar

Requisitos: JDK 17, Android SDK 35 y Build Tools 35.0.0. Configura `ANDROID_HOME` o un `local.properties` con `sdk.dir`. El wrapper incluido descarga Gradle 8.11.1 y verifica su SHA-256.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

En Windows usa `gradlew.bat`. El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

```sh
./gradlew connectedDebugAndroidTest
```

La segunda orden requiere emulador o dispositivo conectado. GitHub Actions ejecuta las pruebas unitarias de precios/radios/filtros/avisos/persistencia, Android Lint, compilación del APK y una prueba de interfaz en emulador Android 15 con datos locales de prueba. Los informes y una captura se publican en los artifacts de verificación. Los datos de prueba no forman parte del funcionamiento normal de la app.

Las pruebas automatizadas no certifican recepción GPS en un móvil real ni la entrega exacta de notificaciones bajo las restricciones de cada fabricante. Para probarlo en tu móvil: conceder y denegar permisos, desactivar Internet tras una descarga, cambiar radios y carburantes, abrir Maps y dejar los avisos habilitados durante un ciclo de actualización.

## Estructura

- `Models.kt`: contrato oficial, importes exactos, distancias y criterio de avisos.
- `FuelDatabase.kt`: caché transaccional, histórico y rechazo de respuestas antiguas.
- `Repository.kt`: descarga, ajustes y acceso local.
- `LocationHelper.kt`: ubicación con cancelación y tiempo de espera.
- `PriceWorker.kt`: actualización periódica y notificaciones.
- `FuelViewModel.kt` / `MainActivity.kt`: estado, permisos e interfaz Compose.
- `.github/workflows/android.yml`: compilación, pruebas y artifacts.
