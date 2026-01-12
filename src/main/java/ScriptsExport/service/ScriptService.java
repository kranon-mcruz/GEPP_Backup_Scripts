package ScriptsExport.service;

import ScriptsExport.config.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mypurecloud.sdk.v2.ApiClient;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.*;

@Service
public class ScriptService {

    @Autowired
    private GenesysConfig config;

    private final RestTemplate rest = new RestTemplate();

    public void downloadAllScriptsAsync(String org) {
        new Thread(() -> {
            try {
                GenesysConfig.Credential cred = config.getCredentials().get(org);
                String apiBaseUrl = cred.getHost().replace("apps.", "api.").replaceAll("/+$", "");
                if (!apiBaseUrl.startsWith("http")) apiBaseUrl = "https://" + apiBaseUrl;

                String token = authenticateAndGetToken(new OrgCredential(apiBaseUrl, cred.getClientId(), cred.getClientSecret()));

                List<Map<String, Object>> listaScripts = listScripts(apiBaseUrl, token);

                String rutaBase = config.getPath();
                if (!rutaBase.endsWith("/")) rutaBase += "/";

                // Carpeta temporal específica para esta ejecución
                String nombreCarpetaOrg = org + "_Scripts";
                String rutaOrganizacion = rutaBase + nombreCarpetaOrg + "/";
                Files.createDirectories(Paths.get(rutaOrganizacion));

                System.out.println("Iniciando exportación de " + listaScripts.size() + " Scripts para " + org);

                int descargados = 0;
                for (Map<String, Object> script : listaScripts) {
                    try {
                        exportarYDescargarScript((String) script.get("id"), (String) script.get("name"),
                                rutaOrganizacion, token, apiBaseUrl);
                        descargados++;
                        Thread.sleep(400); // Throttle para evitar 429
                    } catch (Exception e) {
                        System.err.println("Falló Script " + script.get("name") + ": " + e.getMessage());
                    }
                }

                // 1. Generar Excel DENTRO de la carpeta
                generarExcelReporte(org, rutaOrganizacion, listaScripts);

                // 2. Comprimir la carpeta y borrar la carpeta temporal
                comprimirYLimpiar(org, rutaBase, nombreCarpetaOrg);

                System.out.println("Proceso completado para " + org + ". " + descargados + " scripts en el ZIP.");

            } catch (Exception e) {
                System.err.println("Error crítico: " + e.getMessage());
            }
        }).start();
    }

    private void exportarYDescargarScript(String scriptId, String name, String rutaOrg, String token, String apiBaseUrl) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String urlExport = apiBaseUrl + "/api/v2/scripts/" + scriptId + "/export";
        HttpEntity<String> entity = new HttpEntity<>("{}", headers);

        ResponseEntity<Map> exportResponse = rest.exchange(urlExport, HttpMethod.POST, entity, Map.class);
        String downloadUrl = (String) exportResponse.getBody().get("url");

        if (downloadUrl != null) {
            String nombreLimpio = name.replaceAll("\\s+", "-").replaceAll("[^a-zA-Z0-9-]", "");
            String ts = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(java.time.LocalDateTime.now());
            File archivoDestino = new File(rutaOrg + nombreLimpio + "--" + ts + ".script");

            // Conexión nativa para evitar 403 de S3
            java.net.URL url = new java.net.URL(downloadUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() == 200) {
                try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(archivoDestino)) {
                    in.transferTo(out);
                }
                System.out.println("Script descargado: " + name);
            }
            conn.disconnect();
        }
    }

    private List<Map<String, Object>> listScripts(String apiBaseUrl, String token) {
        List<Map<String, Object>> results = new ArrayList<>();
        int pageNumber = 1, pageCount = 1;
        do {
            String url = apiBaseUrl + "/api/v2/scripts?pageSize=100&pageNumber=" + pageNumber;
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            ResponseEntity<Map> resp = rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Map<String, Object> body = resp.getBody();

            if (body != null && body.containsKey("entities")) {
                List<Map<String, Object>> entities = (List<Map<String, Object>>) body.get("entities");
                for (Map<String, Object> entity : entities) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", entity.get("id"));
                    item.put("name", entity.get("name"));
                    // Capturamos datePublished de la API
                    item.put("publishedDate", entity.getOrDefault("publishedDate", ""));
                    results.add(item);
                }
                pageCount = (Integer) body.get("pageCount");
            }
            pageNumber++;
        } while (pageNumber <= pageCount);
        return results;
    }

    private void generarExcelReporte(String org, String rutaDestino, List<Map<String, Object>> datos) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Scripts");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("NOMBRE");
            header.createCell(1).setCellValue("FECHA ULTIMA PUBLICACIÓN");

            int rowNum = 1;
            for (Map<String, Object> s : datos) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue((String) s.get("name"));
                String fecha = (String) s.get("publishedDate");
                row.createCell(1).setCellValue(fecha.contains("T") ? fecha.split("T")[0] : fecha);
            }
            sheet.autoSizeColumn(0); sheet.autoSizeColumn(1);
            try (FileOutputStream out = new FileOutputStream(rutaDestino + "Reporte_Scripts_" + org + ".xlsx")) {
                workbook.write(out);
            }
        } catch (Exception e) { System.err.println("Error Excel: " + e.getMessage()); }
    }

    private void comprimirYLimpiar(String org, String rutaBase, String nombreCarpeta) {
        String fecha = DateTimeFormatter.ofPattern("ddMMyyyy").format(LocalDate.now());
        String nombreZip = "Scripts_" + org + "_" + fecha + ".zip";
        File carpetaSource = new File(rutaBase + nombreCarpeta);
        File zipDestino = new File(rutaBase + nombreZip);

        try {
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipDestino))) {
                Files.walk(carpetaSource.toPath())
                        .filter(p -> !Files.isDirectory(p))
                        .forEach(p -> {
                            try {
                                zos.putNextEntry(new ZipEntry(carpetaSource.toPath().relativize(p).toString()));
                                Files.copy(p, zos);
                                zos.closeEntry();
                            } catch (IOException e) { e.printStackTrace(); }
                        });
            }
            // Borrar carpeta temporal
            Files.walk(carpetaSource.toPath()).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            System.out.println("ZIP generado exitosamente: " + nombreZip);
        } catch (Exception e) { System.err.println("Error ZIP: " + e.getMessage()); }
    }

    private String authenticateAndGetToken(OrgCredential creds) {
        ApiClient client = ApiClient.Builder.standard().withBasePath(creds.getHost()).build();
        try {
            return client.authorizeClientCredentials(creds.getClientId(), creds.getClientSecret()).getBody().getAccess_token();
        } catch (Exception e) { return null; }
    }
}