import http.server
import socketserver
import os

PORT = 8080


class SPAHandler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        # If the request has no extension and it's not the root, try appending .html
        if "." not in self.path and self.path != "/":
            possible_path = self.path + ".html"
            if os.path.exists(self.translate_path(possible_path)):
                self.path = possible_path

        return super().do_GET()


with socketserver.TCPServer(("", PORT), SPAHandler) as httpd:
    print(f"KittyTune Local SPA Server running on http://localhost:{PORT}")
    httpd.serve_forever()
