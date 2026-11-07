/*
 * Prueba valida para el estado actual del compilador.
 * No usa printf ni scanf (eso se prueba despues del paso 7).
 * Debe compilar sin errores lexicos, sintacticos ni semanticos.
 */

int total;
char inicial;

int factorial(int n);
int sumaHasta(int n);
char normalizar(char c);
int registrar(char *msg, ...);   /* prototipo con char * y ... (paso 6) */

int factorial(int n)
{
    if (n <= 1)
        return 1;
    else
        return n * factorial(n - 1);
}

int sumaHasta(int n)
{
    int i, suma;

    i = 1;
    suma = 0;
    while (i <= n) {
        suma = suma + i;
        i = i + 1;
    }
    return suma;
}

char normalizar(char c)
{
    if (c == 'A')
        return 'a';
    return c;
}

int main(void)
{
    int a, b, limite, mascara, minimo;
    char letra;

    a = 5;
    b = factorial(a);
    limite = 0xFF;           /* hexadecimal con 0x */
    mascara = 0X1F;          /* hexadecimal con 0X (paso 2) */
    minimo = -32768;         /* limite inferior exacto (paso 3) */
    letra = normalizar('A');
    inicial = letra;
    total = sumaHasta(10) + b * 2 - (limite / 5);

    while (a > 0) {
        a = a - 1;
        if (a == 2)
            continue;
        if (a < 1)
            break;
        total = total + a;
    }

    if (letra != 'a') {
        total = -total;
    } else {
        total = total + mascara;
    }

    registrar("total", total);   /* variadica: 1 fijo + 1 extra (paso 6) */
    return 0;
}
