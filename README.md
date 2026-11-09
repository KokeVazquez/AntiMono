# AntiMono 🛡️

**AntiMono** es una aplicación de Android diseñada para el autocontrol digital y la prevención de recaídas. Permite bloquear contenido para adultos, restringir dominios web específicos (lista negra personalizada), proteger el acceso con PIN y dificultar la desinstalación no autorizada mediante permisos de administración de dispositivos y accesibilidad.

---

## 🚀 Características Principales

1. **Bloqueo Inteligente de Sitios Web:**
   - Detecta dominios en la barra de direcciones de navegadores populares (Google Chrome, Samsung Internet, Microsoft Edge, Brave, Firefox, Opera, etc.).
   - Bloquea al instante dominios de tu lista negra personalizada (ej. `x.com`, `twitter.com`, redes sociales).
   - Bloqueo automático de palabras clave y sitios para adultos (+50,000 dominios/términos NSFW precargados).

2. **Bloqueo de Modo Incógnito / Pestañas Secretas:**
   - Cierra o expulsa al escritorio cuando se detecta el uso de pestañas de incógnito en los navegadores.

3. **Protección Anti-Desinstalación:**
   - **Administrador de Dispositivos (`DeviceAdminReceiver`):** Impide que la app sea desinstalada mediante una pulsación larga en la pantalla de inicio de Samsung One UI (el sistema muestra que es un administrador activo).
   - **Filtro Quirúrgico de Ajustes:** Si se intenta desinstalar o revocar el permiso de administrador desde Ajustes sin ingresar el PIN en AntiMono, el sistema expulsa al usuario al escritorio de inmediato (mientras que Wi-Fi, Bluetooth y demás ajustes siguen funcionando normalmente).

4. **Bajo Consumo de Batería:**
   - Funciona mediante eventos de accesibilidad reactivos (el procesador duerme el 99.9% del tiempo y solo inspecciona la pantalla durante milisegundos cuando hay interacción).

5. **Protección por PIN:**
   - Toda la configuración, activación/desactivación y lista negra está protegida por un PIN numérico encriptado (SHA-256).

---

## 📱 Cómo abrir y compilar en Android Studio

1. Abre **Android Studio**.
2. Selecciona **Open** (Abrir) y busca la carpeta del proyecto:
   `c:\Users\Admin\Desktop\PRACTICA PYTHON\AntiMono`
3. Espera a que Gradle sincronice las dependencias del proyecto automáticamente.
4. Conecta tu teléfono **Samsung (ej. S26 Ultra)** a la computadora por cable USB (asegúrate de tener activada la **Depuración por USB** en *Opciones de Desarrollador*).
5. Haz clic en el botón verde **Run ▶** en Android Studio para instalarla en tu teléfono.

---

## ⚙️ Primeros Pasos en el Teléfono

Al abrir la aplicación por primera vez:
1. **Crea tu PIN de seguridad** (4 a 8 dígitos).
2. En la pantalla principal:
   - Presiona **"Activar"** en *1. Accesibilidad* -> Busca **AntiMono** y concédele el permiso.
   - Presiona **"Activar"** en *2. Anti-Desinstalación* -> Concédele el permiso de Administrador de Dispositivos.
   - Presiona **"Desactivar optimización de batería"** para que Samsung no cierre el servicio en segundo plano.
3. ¡Listo! Agrega los dominios que desees en la sección de *Lista Negra* (ej. `x.com`) y disfruta de la protección activa.
