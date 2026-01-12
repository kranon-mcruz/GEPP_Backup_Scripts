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

    public String downloadAllScriptsSync(String org) {
        int descargados = 0;
        int total = 0;

        try {
            GenesysConfig.Credential cred = config.getCredentials().get(org);
            String apiBaseUrl = cred.getHost().replace("apps.", "api.").replaceAll("/+$", "");
            if (!apiBaseUrl.startsWith("http")) apiBaseUrl = "https://" + apiBaseUrl;

            String token = authenticateAndGetToken(new OrgCredential(apiBaseUrl, cred.getClientId(), cred.getClientSecret()));

            List<Map<String, Object>> listaScripts = listScripts(apiBaseUrl, token);
            total = listaScripts.size();

            String rutaBase = config.getPath();
            if (!rutaBase.endsWith("/")) rutaBase += "/";

            String nombreCarpetaOrg = org + "_Scripts";
            String rutaOrganizacion = rutaBase + nombreCarpetaOrg + "/";
            Files.createDirectories(Paths.get(rutaOrganizacion));

            System.out.println("Iniciando exportación de " + total + " Scripts para " + org);

            for (Map<String, Object> script : listaScripts) {
                boolean completado = false;
                int intentos = 0;

                while (!completado && intentos < 3) {
                    try {
                        exportarYDescargarScript((String) script.get("id"), (String) script.get("name"),
                                rutaOrganizacion, token, apiBaseUrl);

                        descargados++;
                        completado = true;
                        Thread.sleep(400); // Respetar el Rate Limit
                    } catch (Exception e) {
                        intentos++;
                        System.err.println("Falló Script " + script.get("name") + " (Intento " + intentos + "): " + e.getMessage());

                        if (e.getMessage().contains("429")) {
                            System.out.println("⚠️ Rate Limit alcanzado en Scripts. Pausando 15 segundos...");
                            Thread.sleep(15000);
                        } else {
                            System.err.println("Error no recuperable en script. Saltando.");
                            completado = true;
                        }
                    }
                }
            }

            // Generar reporte y limpiar
            generarExcelReporte(org, rutaOrganizacion, listaScripts);
            comprimirYLimpiar(org, rutaBase, nombreCarpetaOrg);

        } catch (Exception e) {
            System.err.println("Error crítico en Scripts: " + e.getMessage());
            return "Error crítico: " + e.getMessage();
        }

        // Este mensaje será lo que verás en Postman al finalizar
        return "Proceso de Scripts completado para " + org + ". " + descargados + " de " + total + " scripts procesados exitosamente.";
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
                    item.put("modifiedDate", entity.getOrDefault("modifiedDate", ""));
                    item.put("division", entity.get("division"));
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
            header.createCell(1).setCellValue("FECHA ULTIMA MODIFICACIÓN");
            header.createCell(2).setCellValue("FECHA ULTIMA PUBLICACIÓN");
            header.createCell(3).setCellValue("DIVISIÓN");

            int rowNum = 1;
            for (Map<String, Object> s : datos) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue((String) s.get("name"));
                String fechamodificacion = (String) s.get("modifiedDate");
                row.createCell(1).setCellValue(fechamodificacion.contains("T") ? fechamodificacion.split("T")[0] : fechamodificacion);
                String fecha = (String) s.get("publishedDate");
                row.createCell(2).setCellValue(fecha.contains("T") ? fecha.split("T")[0] : fecha);
                Map<String, Object> divisionInfo = (Map<String, Object>) s.get("division");
                String nombreDivision = "Unassigned"; // Valor por defecto si no existe

                if (divisionInfo != null && divisionInfo.containsKey("name")) {
                    nombreDivision = (String) divisionInfo.get("name");
                }

                row.createCell(3).setCellValue(nombreDivision);
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