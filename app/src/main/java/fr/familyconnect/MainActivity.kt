package fr.familyconnect

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.*
import android.graphics.Typeface
import java.io.*
import java.net.*
import java.util.Base64
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private val pickCode = 4102
    private val port = 8989
    private val executor = Executors.newSingleThreadExecutor()

    private var role = "parent"
    private var server: ServerSocket? = null
    private var pin = ""

    private lateinit var page: LinearLayout
    private lateinit var status: TextView
    private lateinit var actions: LinearLayout
    private lateinit var listing: LinearLayout
    private lateinit var parentAddress: TextView
    private lateinit var childAddress: EditText
    private lateinit var childPin: EditText

    private val sharedDir by lazy {
        File(filesDir, "FamilyConnect").apply { mkdirs() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)

        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 28, 36, 28)
        }

        scroll.addView(page)
        setContentView(scroll)

        page.addView(label("Family Connect", 28, true))
        page.addView(label("Partage familial direct, sans compte cloud", 15, false))
        page.addView(label("Les deux téléphones doivent être sur le même Wi-Fi.", 14, false))
        page.addView(label("Choisis le mode de ce téléphone", 18, true))

        val roles = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
        }

        val child = RadioButton(this).apply {
            id = 101
            text = "Téléphone enfant — envoyer des fichiers"
        }

        val parent = RadioButton(this).apply {
            id = 102
            text = "Téléphone parent — recevoir les fichiers"
            isChecked = true
        }

        roles.addView(child)
        roles.addView(parent)
        page.addView(roles)

        actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        page.addView(actions)

        status = label(
            "Choisis le mode parent pour démarrer la réception.",
            14,
            false
        )

        page.addView(status)

        listing = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        page.addView(listing)

        roles.setOnCheckedChangeListener { _, checked ->
            role = if (checked == child.id) "child" else "parent"
            renderActions()
        }

        renderActions()
    }

    private fun label(s: String, size: Int, bold: Boolean) =
        TextView(this).apply {
            text = s
            textSize = size.toFloat()
            setPadding(0, 10, 0, 10)

            if (bold) {
                setTypeface(null, Typeface.BOLD)
            }
        }

    private fun edit(hintText: String, secret: Boolean = false) =
        EditText(this).apply {
            hint = hintText
            textSize = 15f

            if (secret) {
                inputType = 2
            }
        }

    private fun renderActions() {
        actions.removeAllViews()
        listing.removeAllViews()

        if (role == "parent") {

            pin = (100000..999999).random().toString()

            parentAddress = label(
                "Adresse du parent : recherche…",
                16,
                true
            )

            actions.addView(parentAddress)

            actions.addView(
                label("Code de connexion : $pin", 22, true)
            )

            actions.addView(
                label(
                    "Garde cette page ouverte. Sur le téléphone enfant, saisis l'adresse IP et ce code.",
                    14,
                    false
                )
            )

            actions.addView(Button(this).apply {
                text = "Démarrer la réception"
                setOnClickListener {
                    startServer()
                }
            })

            actions.addView(Button(this).apply {
                text = "Actualiser les fichiers reçus"
                setOnClickListener {
                    showFiles()
                }
            })

            parentAddress.text =
                "Adresse du parent : ${localIp() ?: "IP Wi-Fi introuvable"}:$port"

            showFiles()

        } else {

            childAddress = edit(
                "Adresse IP du téléphone parent (ex. 192.168.1.20)"
            )

            childPin = edit(
                "Code à 6 chiffres du parent",
                true
            )

            actions.addView(
                label(
                    "Saisis les informations affichées sur le téléphone parent.",
                    14,
                    false
                )
            )

            actions.addView(childAddress)
            actions.addView(childPin)

            actions.addView(Button(this).apply {
                text = "Sélectionner des photos, vidéos ou fichiers"

                setOnClickListener {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }

                    startActivityForResult(intent, pickCode)
                }
            })
        }
    }

    // Recherche de l'adresse IP locale
    private fun localIp(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()

            while (interfaces.hasMoreElements()) {
                val addresses =
                    interfaces.nextElement().inetAddresses

                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()

                    if (!address.isLoopbackAddress &&
                        address is Inet4Address &&
                        address.isSiteLocalAddress
                    ) {
                        return address.hostAddress
                    }
                }
            }
        } catch (_: Exception) {
            return null
        }

        return null
    }

    // Démarrage du serveur parent
    private fun startServer() {

        if (server != null) {
            status.text = "Réception déjà démarrée."
            return
        }

        thread(
            name = "FamilyConnect-server",
            isDaemon = true
        ) {
            try {
                val socketServer = ServerSocket(port)
                server = socketServer

                runOnUiThread {
                    status.text =
                        "Réception active. Laisse Family Connect ouvert."
                }

                while (!socketServer.isClosed) {
                    val client = socketServer.accept()

                    thread(isDaemon = true) {
                        handleClient(client)
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Serveur arrêté : ${e.message}"
                }
            }
        }
    }

    // Traitement des fichiers reçus
    private fun handleClient(socket: Socket) {

        socket.use { s ->

            try {
                s.soTimeout = 30000

                val input = BufferedInputStream(s.getInputStream())
                val headerBytes = ByteArrayOutputStream()

                var state = 0

                while (state < 4) {
                    val b = input.read()

                    if (b < 0) return

                    headerBytes.write(b)

                    state = when {
                        state == 0 && b == 13 -> 1
                        state == 1 && b == 10 -> 2
                        state == 2 && b == 13 -> 3
                        state == 3 && b == 10 -> 4
                        b == 13 -> 1
                        else -> 0
                    }

                    if (headerBytes.size() > 16384) return
                }

                val header = headerBytes.toString("UTF-8")

                val first = header.lineSequence()
                    .firstOrNull()
                    .orEmpty()

                val method = first.substringBefore(' ')

                val path = first.split(' ')
                    .getOrNull(1)
                    .orEmpty()

                val headers = header.lineSequence()
                    .drop(1)
                    .mapNotNull { line ->
                        val idx = line.indexOf(':')

                        if (idx > 0) {
                            line.substring(0, idx)
                                .trim()
                                .lowercase() to
                                line.substring(idx + 1).trim()
                        } else {
                            null
                        }
                    }
                    .toMap()

                if (headers["x-family-code"] != pin) {
                    respond(s, 403, "Code incorrect")
                    return
                }

                if (method == "POST" && path.startsWith("/upload")) {

                    val length =
                        headers["content-length"]?.toIntOrNull()
                            ?: run {
                                respond(s, 411, "Longueur manquante")
                                return
                            }

                    if (length > 50 * 1024 * 1024) {
                        respond(s, 413, "Fichier trop volumineux")
                        return
                    }

                    val nameParam = path.substringAfter(
                        "name=",
                        "fichier"
                    ).substringBefore('&')

                    val name = URLDecoder.decode(
                        nameParam,
                        "UTF-8"
                    )
                        .replace("/", "_")
                        .replace("\\", "_")
                        .take(180)
                        .ifBlank { "fichier" }

                    val target = uniqueFile(name)

                    FileOutputStream(target).use { out ->

                        val buffer = ByteArray(8192)
                        var remaining = length

                        while (remaining > 0) {

                            val n = input.read(
                                buffer,
                                0,
                                minOf(buffer.size, remaining)
                            )

                            if (n < 0) {
                                throw EOFException("Transfert interrompu")
                            }

                            out.write(buffer, 0, n)
                            remaining -= n
                        }
                    }

                    respond(s, 200, "OK")

                    runOnUiThread {
                        status.text = "Fichier reçu : ${target.name}"
                        showFiles()
                    }

                } else if (method == "GET" && path == "/files") {

                    val body = sharedDir.listFiles()
                        ?.filter { it.isFile }
                        ?.joinToString("\n") {
                            Base64.getEncoder().encodeToString(
                                it.name.toByteArray(Charsets.UTF_8)
                            )
                        }
                        .orEmpty()

                    respond(s, 200, body)

                } else {
                    respond(s, 404, "Introuvable")
                }

            } catch (e: Exception) {

                try {
                    respond(s, 500, e.message ?: "Erreur")
                } catch (_: Exception) {
                }
            }
        }
    }

    // Création d'un nom de fichier unique
    private fun uniqueFile(name: String): File {

        var file = File(sharedDir, name)
        var i = 1

        while (file.exists()) {

            val dot = name.lastIndexOf('.')

            val stem =
                if (dot > 0) name.substring(0, dot) else name

            val ext =
                if (dot > 0) name.substring(dot) else ""

            file = File(sharedDir, "$stem ($i)$ext")
            i++
        }

        return file
    }

    // CORRECTION : écriture HTTP en deux étapes
    private fun respond(s: Socket, code: Int, body: String) {

        val bytes = body.toByteArray(Charsets.UTF_8)

        val reason = when (code) {
            200 -> "OK"
            403 -> "Forbidden"
            404 -> "Not Found"
            411 -> "Length Required"
            413 -> "Payload Too Large"
            else -> "Error"
        }

        val responseHeaders =
            "HTTP/1.1 $code $reason\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"

        val out = s.getOutputStream()

        out.write(responseHeaders.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    // Sélection et envoi des fichiers
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != pickCode ||
            resultCode != RESULT_OK ||
            data == null
        ) {
            return
        }

        val uris = mutableListOf<Uri>()

        // CORRECTION : getItemAt(i)
        data.clipData?.let { clip ->

            for (i in 0 until clip.itemCount) {
                uris.add(clip.getItemAt(i).uri)
            }
        }

        if (uris.isEmpty()) {
            data.data?.let {
                uris.add(it)
            }
        }

        val host = childAddress.text.toString()
            .trim()
            .removePrefix("http://")
            .substringBefore(':')

        val code = childPin.text.toString().trim()

        if (host.isBlank() || code.length != 6) {
            status.text =
                "Entre l'adresse IP et le code à 6 chiffres."
            return
        }

        status.text = "Envoi en cours…"

        executor.execute {

            var sent = 0
            val errors = mutableListOf<String>()

            uris.forEach { uri ->

                try {
                    val name = queryName(uri)

                    val bytes = contentResolver
                        .openInputStream(uri)
                        ?.use { it.readBytes() }
                        ?: error("Lecture impossible")

                    if (bytes.size > 50 * 1024 * 1024) {
                        error("$name dépasse 50 Mo")
                    }

                    upload(host, code, name, bytes)
                    sent++

                } catch (e: Exception) {
                    errors.add(e.message ?: "Erreur")
                }
            }

            runOnUiThread {
                status.text =
                    "$sent/${uris.size} fichier(s) envoyé(s). " +
                    (errors.firstOrNull()?.let {
                        "Erreur : $it"
                    } ?: "")
            }
        }
    }

    // Récupération du nom du fichier
    private fun queryName(uri: Uri): String {

        var name = "fichier"

        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use {

            if (it.moveToFirst()) {
                name = it.getString(0) ?: name
            }
        }

        return name
            .replace("/", "_")
            .replace("\\", "_")
    }

    // Envoi vers le téléphone parent
    private fun upload(
        host: String,
        code: String,
        name: String,
        bytes: ByteArray
    ) {

        Socket().use { socket ->

            socket.connect(
                InetSocketAddress(host, port),
                10000
            )

            socket.soTimeout = 30000

            val encoded = URLEncoder.encode(name, "UTF-8")

            val headers =
                "POST /upload?name=$encoded HTTP/1.1\r\n" +
                "Host: $host\r\n" +
                "X-Family-Code: $code\r\n" +
                "Content-Type: application/octet-stream\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"

            val out = BufferedOutputStream(
                socket.getOutputStream()
            )

            out.write(headers.toByteArray(Charsets.UTF_8))
            out.write(bytes)
            out.flush()

            val response = BufferedReader(
                InputStreamReader(socket.getInputStream())
            ).readLine()

            if (response == null || !response.contains(" 200 ")) {
                error("Réponse du parent : ${response ?: "aucune"}")
            }
        }
    }

    // Affichage des fichiers reçus
    private fun showFiles() {

        listing.removeAllViews()

        val files = sharedDir.listFiles()
            ?.filter { it.isFile }
            .orEmpty()

        if (files.isEmpty()) {
            listing.addView(
                label("Aucun fichier reçu pour le moment.", 14, false)
            )
        }

        files.sortedBy { it.name.lowercase() }.forEach { file ->

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            row.addView(TextView(this).apply {
                text = "${file.name} (${file.length() / 1024} Ko)"
                textSize = 14f

                layoutParams = LinearLayout.LayoutParams(
                    0,
                    -2,
                    1f
                )
            })

            row.addView(Button(this).apply {
                text = "Ouvrir"

                setOnClickListener {
                    openFile(file)
                }
            })

            listing.addView(row)
        }
    }

    // Ouverture d'un fichier
    private fun openFile(file: File) {

        val uri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "$packageName.provider",
            file
        )

        val mime = when (file.extension.lowercase()) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "mp4" -> "video/mp4"
            else -> "*/*"
        }

        try {
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, mime)
                    .addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
            )
        } catch (_: Exception) {
            status.text =
                "Aucune application ne peut ouvrir ce fichier."
        }
    }

    override fun onDestroy() {

        try {
            server?.close()
        } catch (_: Exception) {
        }

        executor.shutdown()

        super.onDestroy()
    }
}
