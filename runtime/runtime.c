#include <stdint.h>
#include <stdio.h>

void microc_printf_text(const char *formato) {
    printf(formato);
}

void microc_printf_i16(const char *formato, int16_t valor) {
    printf(formato, (int) valor);
}

void microc_printf_i8(const char *formato, int8_t valor) {
    printf(formato, (int) valor);
}

int16_t microc_scan_i16(const char *formato) {
    int valor = 0;

    if (scanf(formato, &valor) != 1) {
        return 0;
    }

    return (int16_t) valor;
}

int8_t microc_scan_i8(const char *formato) {
    char valor = 0;

    if (scanf(formato, &valor) != 1) {
        return 0;
    }

    return (int8_t) valor;
}