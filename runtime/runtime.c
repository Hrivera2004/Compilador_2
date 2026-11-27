#include <stdint.h>
#include <stdio.h>

//Desactiva el buffering de stdout una sola vez
static int microc_io_iniciado = 0;

static void microc_init_io(void) {
    if (!microc_io_iniciado) {
        setvbuf(stdout, NULL, _IONBF, 0);
        microc_io_iniciado = 1;
    }
}

void microc_printf_text(const char *formato) {
    microc_init_io();
    printf(formato);
    fflush(stdout);
}

void microc_printf_i16(const char *formato, int16_t valor) {
    microc_init_io();
    printf(formato, (int) valor);
    fflush(stdout);
}

void microc_printf_i8(const char *formato, int8_t valor) {
    microc_init_io();
    printf(formato, (int) valor);
    fflush(stdout);
}

int16_t microc_scan_i16(const char *formato) {
    microc_init_io();
    int valor = 0;

    if (scanf(formato, &valor) != 1) {
        return 0;
    }

    return (int16_t) valor;
}

int8_t microc_scan_i8(const char *formato) {
    microc_init_io();
    char valor = 0;

    (void) formato;

    //El espacio antes de %c descarta espacios
    if (scanf(" %c", &valor) != 1) {
        return 0;
    }

    return (int8_t) valor;
}