package rotlin.runtime

import java.net.URLEncoder

internal object Html {

    fun esc(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    fun escAttr(text: String): String = esc(text).replace("\"", "&quot;")

    fun urlEnc(text: String): String = URLEncoder.encode(text, Charsets.UTF_8)

    private val CSS = """
        :root { color-scheme: dark; }
        body {
          background: #0f1115; color: #e8e8ea;
          font-family: 'Segoe UI', system-ui, sans-serif;
          max-width: 640px; margin: 3rem auto; padding: 0 1.5rem;
          line-height: 1.6;
        }
        h1 {
          font-size: 2.6rem; line-height: 1.15; margin: 0 0 1rem;
          background: linear-gradient(90deg, #ff4ecd, #7b5bff, #22d3ee);
          -webkit-background-clip: text; background-clip: text; color: transparent;
        }
        p { font-size: 1.15rem; margin: 0.5rem 0; }
        img { max-width: 100%; border-radius: 16px; margin: 0.75rem 0; }
        form { display: inline-block; margin: 0.75rem 0.5rem 0 0; }
        button {
          font-size: 1.2rem; font-weight: 700; cursor: pointer;
          color: #fff; background: linear-gradient(135deg, #ff4ecd, #7b5bff);
          border: none; border-radius: 14px; padding: 0.7rem 1.6rem;
          transition: transform 0.06s ease;
        }
        button:hover { transform: scale(1.06); }
        button:active { transform: scale(0.94); }
        a { color: #22d3ee; }
        #rot-roast {
          position: fixed; inset: auto 0 0 0; margin: 0; padding: 1rem 1.5rem;
          background: #2a0a12; color: #ff7a90; white-space: pre-wrap;
          font-family: Consolas, monospace; font-size: 0.95rem;
          border-top: 3px solid #ff2d55; max-height: 45vh; overflow: auto;
        }
    """.trimIndent()

    private val DEV_SCRIPT = """
        <script>
        (function () {
          var es = new EventSource('/__rotlin/events');
          es.onmessage = function (e) { if (e.data === 'reload') location.reload(); };
          es.addEventListener('aura', function (e) {
            var el = document.getElementById('rot-roast');
            if (!el) {
              el = document.createElement('pre');
              el.id = 'rot-roast';
              document.body.appendChild(el);
            }
            el.textContent = e.data;
          });
        })();
        </script>
    """.trimIndent()

    fun shell(body: String, devMode: Boolean): String = buildString {
        append("<!doctype html>\n<html lang=\"en\">\n<head>\n")
        append("<meta charset=\"utf-8\">\n")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
        append("<title>rotlin page</title>\n")
        append("<style>\n").append(CSS).append("\n</style>\n")
        append("</head>\n<body>\n")
        append(body)
        if (devMode) append("\n").append(DEV_SCRIPT).append("\n")
        append("</body>\n</html>\n")
    }
}
