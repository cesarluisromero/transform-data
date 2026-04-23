package com.blessrom.transform.data.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.jsoup.Jsoup;

@RegisterForReflection
public record ProductoDTO(
        String id,
        String nombre,
        String descripcionOriginal,
        Double precio,
        Integer stock,
        String fechaModificacion,
        String imageUrl // <-- Nuevo campo para la miniatura
) {
    public String descripcionLimpia() {
        if (descripcionOriginal == null) return "";
        return Jsoup.parse(descripcionOriginal).text();
    }
}