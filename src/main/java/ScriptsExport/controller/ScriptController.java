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
    public ResponseEntity<String> exportScripts(@RequestParam String org) {

        String respuesta = scriptService.downloadAllScriptsSync(org);
        return ResponseEntity.ok(respuesta);
    }
}