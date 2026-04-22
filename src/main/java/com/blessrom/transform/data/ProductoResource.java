package com.blessrom.transform.data;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import com.blessrom.transform.data.dto.ProductoDTO;

@Path("/api/v1/productos")
public class ProductoResource {

    @Inject
    SincronizadorService sincronizador;

    @GET
    @Path("/buscar")
    @Produces(MediaType.TEXT_PLAIN) // El bot prefiere texto plano para responder directo
    public String buscar(@QueryParam("q") String consulta) {
        if (consulta == null || consulta.isEmpty()) {
            return "Por favor, dime qué estás buscando.";
        }
        // Llama a tu motor de Weaviate que ya configuramos
        return sincronizador.buscar(consulta);
    }

    @GET
    @Path("/todos")
    @Produces(MediaType.APPLICATION_JSON)
    public List<ProductoDTO> obtenerTodos() {
        return sincronizador.obtenerTodos();
    }
}