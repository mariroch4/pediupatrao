package com.umc.pediupatrao.dto;

import java.math.BigDecimal;

public record DescontoRequest(BigDecimal percentual, String motivo) {
}
