# Security

Space Camera runs entirely on the device: no server, no account, no network calls. It
writes photos and videos to the device's MediaStore and, only if "Save location" is on,
GPS coordinates to the photo's EXIF.

If you find a vulnerability — for example a way to read media or location the app
shouldn't expose — please report it privately through GitHub: **Security → Report a
vulnerability** on this repository. Please don't open a public issue for it.
