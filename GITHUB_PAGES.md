# Lanzador de TV Premium - Sitio Web de Descargas

Este proyecto incluye una página de descargas moderna con diseño **Bento Grid** estilo Android TV. Aquí tienes las instrucciones para publicarla totalmente gratis usando **GitHub Pages**.

---

## Estructura de la Web
Los archivos de la página están organizados en:
*   `website/index.html` - Estructura principal y lógica de actualización dinámica.
*   `website/style.css` - Estilos adicionales y animaciones fluidas.
*   `website/update.json` - Datos de versión, enlaces de descarga y lista de cambios (changelog).
*   `downloads/Lanzador-TV-Premium.apk` - El archivo ejecutable APK más reciente.

---

## Método 1: Desplegar usando una Acción de GitHub (Recomendado y Automatizado)

Este método es el más limpio porque mantiene el código del Launcher en tu repositorio y publica únicamente la carpeta `website` de forma automatizada cada vez que haces un cambio.

### Pasos:

1.  **Sube tu proyecto a GitHub**:
    Crea un repositorio en tu cuenta de GitHub (ej. `Lanzador-TV-Premium`) y sube todos los archivos de este proyecto.

2.  **Configura los Permisos de Páginas**:
    *   En tu repositorio de GitHub, ve a **Settings** (Configuración) > **Pages**.
    *   Bajo **Build and deployment** > **Source**, asegúrate de seleccionar **GitHub Actions**.

3.  **Crea el Workflow de Despliegue**:
    Ya hemos configurado o puedes crear un archivo de acción en tu repositorio para desplegar la carpeta automáticamente. Crea el archivo `.github/workflows/deploy-pages.yml` con el siguiente contenido:

    ```yaml
    name: Deploy Website to GitHub Pages

    on:
      push:
        branches: [ "main" ]
        paths:
          - 'website/**'
          - 'downloads/**'

    permissions:
      contents: read
      pages: write
      id-token: write

    concurrency:
      group: "pages"
      cancel-in-progress: false

    jobs:
      deploy:
        environment:
          name: github-pages
          url: ${{ steps.deployment.outputs.page_url }}
        runs-on: ubuntu-latest
        steps:
          - name: Checkout
            uses: actions/checkout@v4

          - name: Setup Pages
            uses: actions/configure-pages@v4

          - name: Upload Artifact
            uses: actions/upload-pages-artifact@v3
            with:
              # Sube la carpeta website para que sea la raíz de la web
              path: './website'

          - name: Deploy to GitHub Pages
            id: deployment
            uses: actions/deploy-pages@v4
    ```

4.  **Sube la APK de descarga**:
    *   Para que la descarga funcione, coloca el archivo APK generado (`Lanzador-TV-Premium.apk`) dentro de la carpeta `website/downloads/` o ajusta la ruta `apkUrl` en `website/update.json` para que coincida con el enlace absoluto de tu APK.
    *   *Nota*: Si usas el workflow anterior, puedes crear una carpeta `downloads` dentro de `website` (`website/downloads/`) para que se publique junto con el sitio web.

---

## Método 2: Desplegar desde una rama separada (`gh-pages`)

Si prefieres la configuración tradicional de un solo clic de GitHub Pages:

1.  Crea una nueva rama en tu repositorio llamada `gh-pages`:
    ```bash
    git checkout -b gh-pages
    ```
2.  Mueve los archivos de la carpeta `website` a la raíz de la rama `gh-pages` (junto con el archivo APK en una carpeta `downloads/` en la raíz).
3.  Sube la rama a GitHub:
    ```bash
    git push origin gh-pages
    ```
4.  En GitHub, ve a **Settings** > **Pages**. Bajo **Build and deployment** > **Source**, selecciona **Deploy from a branch**, elige la rama `gh-pages` y la carpeta `/ (root)`. ¡Listo!

---

## Cómo Actualizar la Aplicación en el Futuro

Cada vez que publiques una nueva versión de tu Launcher:
1.  Compila la APK actualizada.
2.  Reemplaza el archivo `Lanzador-TV-Premium.apk` con el nuevo APK.
3.  Modifica el archivo `website/update.json` con la nueva versión y el historial de cambios:
    ```json
    {
      "versionCode": 3,
      "versionName": "1.2",
      "releaseDate": "20 de Julio, 2026",
      "apkUrl": "downloads/Lanzador-TV-Premium.apk",
      "downloadText": "Descargar Lanzador de TV Premium v1.2",
      "changelog": [
        "Nueva mejora de rendimiento",
        "Soporte de idioma adicional"
      ]
    }
    ```
4.  Sube los cambios a GitHub. La web se actualizará automáticamente y tus usuarios verán la nueva versión e historial de cambios de inmediato sin necesidad de tocar el código HTML.
