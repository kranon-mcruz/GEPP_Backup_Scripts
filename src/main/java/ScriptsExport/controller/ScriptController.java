package ScriptsExport.controller;


import ScriptsExport.service.ScriptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/export")
public class ScriptController {

    @Autowired
    private ScriptService scriptService;

    // Endpoint: GET http://localhost:8080/api/export/scripts?org=CA
    @GetMapping("/scripts")
    public ResponseEntity<Map<String, String>> exportScripts(@RequestParam String org) {
        try {
            // Iniciamos el proceso asíncrono
            scriptService.downloadAllScriptsAsync(org);

            return ResponseEntity.ok(Map.of(
                    "status", "Proceso iniciado",
                    "mensaje", "La exportación de Scripts para '" + org + "' se está ejecutando en segundo plano."
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "No se pudo iniciar el proceso",
                    "detalle", e.getMessage()
            ));
        }
    }
}