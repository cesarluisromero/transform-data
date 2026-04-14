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
        String fechaModificacion // <-- ¡Este campo es vital para comparar!
) {
    public String descripcionLimpia() {
        if (descripcionOriginal == null) return "";
        return Jsoup.parse(descripcionOriginal).text();
    }
}