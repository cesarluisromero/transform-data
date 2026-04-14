package com.blessrom.transform.data;

import com.blessrom.transform.data.dto.ProductoDTO;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkus.scheduler.Scheduled;
import io.weaviate.client.Config;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.base.Result;
import io.weaviate.client.v1.graphql.model.GraphQLResponse;
import io.weaviate.client.v1.graphql.query.argument.HybridArgument;
import io.weaviate.client.v1.graphql.query.fields.Field;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.*;
import java.util.stream.Collectors;

@RegisterForReflection(targets = {
        io.weaviate.client.base.Result.class,
        io.weaviate.client.base.WeaviateError.class,
        io.weaviate.client.v1.data.model.WeaviateObject.class,
        io.weaviate.client.v1.graphql.model.GraphQLResponse.class,
        io.weaviate.client.v1.graphql.model.GraphQLError.class
})
@ApplicationScoped
public class SincronizadorService {

    private static final Logger LOG = Logger.getLogger(SincronizadorService.class);

    @Inject
    EntityManager emWeb;

    @ConfigProperty(name = "openai.api.key")
    String openAiKey;

    private WeaviateClient client;

    @PostConstruct
    void init() {
        if (openAiKey == null || openAiKey.trim().isEmpty()) {
            LOG.error("❌ API Key de OpenAI no encontrada.");
            return;
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("X-OpenAI-Api-Key", openAiKey);
        Config config = new Config("http", "localhost:8081", headers);

        this.client = new WeaviateClient(config);
        LOG.info("🚀 Cliente Weaviate configurado exitosamente para Blessrom (Modo Incremental).");
    }

    @Scheduled(every = "6h")
    public void sincronizar() {
        if (client == null) return;

        LOG.info("🔄 Iniciando sincronización inteligente de productos...");

        // SQL: Traemos datos filtrando por stock y usando COALESCE para evitar nulos
        String sql = "SELECT p.ID, p.post_title, p.post_content, " +
                "COALESCE(pm_price.meta_value, '0') as precio, " +
                "COALESCE(pm_stock.meta_value, '0') as stock, " +
                "p.post_modified " +
                "FROM wp_posts p " +
                "LEFT JOIN wp_postmeta pm_stock ON p.ID = pm_stock.post_id AND pm_stock.meta_key = '_stock' " +
                "LEFT JOIN wp_postmeta pm_price ON p.ID = pm_price.post_id AND pm_price.meta_key = '_price' " +
                "WHERE p.post_type IN ('product', 'product_variation') " +
                "AND p.post_status = 'publish' " +
                "AND CAST(COALESCE(pm_stock.meta_value, '0') AS UNSIGNED) > 0";

        try {
            List<Object[]> resultados = emWeb.createNativeQuery(sql).getResultList();
            LOG.info("📊 DEBUG: Productos con stock encontrados en MySQL: " + resultados.size());

            int procesados = 0;
            int saltados = 0;

            for (Object[] r : resultados) {
                // 1. Validación de seguridad contra nulos en la fila
                if (r[0] == null || r[4] == null) continue;

                ProductoDTO prod = new ProductoDTO(
                        r[0].toString(),
                        r[1] != null ? r[1].toString() : "Sin nombre",
                        r[2] != null ? r[2].toString() : "",
                        Double.parseDouble(r[3] != null ? r[3].toString() : "0"),
                        Integer.parseInt(r[4].toString()),
                        r[5] != null ? r[5].toString() : ""
                );

                String uuid = generarUUID(prod.id());
                boolean necesitaActualizar = true;
                boolean existeEnWeaviate = false;

                // 2. Comprobar si ya existe en Weaviate para comparar fechas
                var existente = client.data().objectsGetter()
                        .withID(uuid)
                        .withClassName("Producto")
                        .run();

                if (!existente.hasErrors() && existente.getResult() != null && !existente.getResult().isEmpty()) {
                    existeEnWeaviate = true;
                    Map<String, Object> propsActuales = existente.getResult().get(0).getProperties();
                    String fechaGuardada = (String) propsActuales.get("ultima_modificacion");

                    if (prod.fechaModificacion().equals(fechaGuardada)) {
                        necesitaActualizar = false;
                    }
                }

                // 3. Crear o Actualizar (Upsert)
                if (necesitaActualizar) {
                    Map<String, Object> props = new HashMap<>();
                    props.put("nombre", prod.nombre());
                    props.put("descripcion", prod.descripcionLimpia());
                    props.put("precio", prod.precio());
                    props.put("stock", prod.stock());
                    props.put("ultima_modificacion", prod.fechaModificacion());

                    Result<?> response;
                    if (existeEnWeaviate) {
                        response = client.data().updater()
                                .withClassName("Producto")
                                .withID(uuid)
                                .withProperties(props)
                                .run();
                    } else {
                        response = client.data().creator()
                                .withClassName("Producto")
                                .withID(uuid)
                                .withProperties(props)
                                .run();
                    }

                    if (!response.hasErrors()) {
                        procesados++;
                    } else {
                        LOG.error("❌ Error en producto " + prod.id() + ": " + response.getError().getMessages());
                    }
                } else {
                    saltados++;
                }
            }

            LOG.info("✅ Sincronización terminada. Actualizados/Nuevos: " + procesados + ". Sin cambios: " + saltados);

        } catch (Exception e) {
            LOG.error("❌ Error crítico en la sincronización: " + e.getMessage());
        }
    }

    public String buscar(String texto) {
        if (client == null) return "Servicio no disponible.";

        Field nombre = Field.builder().name("nombre").build();
        Field precio = Field.builder().name("precio").build();
        Field descripcion = Field.builder().name("descripcion").build();

        Result<GraphQLResponse> result = client.graphQL().get()
                .withClassName("Producto")
                .withFields(nombre, precio, descripcion)
                .withHybrid(HybridArgument.builder()
                        .query(texto)
                        .alpha(0.5f)
                        .build())
                .withLimit(5)
                .run();

        if (result.hasErrors()) return "Error en la búsqueda.";
        return formatearParaIA(result.getResult());
    }

    private String generarUUID(String wpId) {
        return UUID.nameUUIDFromBytes(("blessrom-" + wpId).getBytes()).toString();
    }

    private String formatearParaIA(GraphQLResponse response) {
        try {
            Map<String, Object> data = (Map<String, Object>) response.getData();
            Map<String, Object> get = (Map<String, Object>) data.get("Get");
            List<Map<String, Object>> lista = (List<Map<String, Object>>) get.get("Producto");

            if (lista == null || lista.isEmpty()) return "No hay productos disponibles.";

            return lista.stream()
                    .map(p -> String.format("- %s (S/ %s): %s",
                            p.get("nombre"), p.get("precio"), p.get("descripcion")))
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "Error al procesar resultados.";
        }
    }
}