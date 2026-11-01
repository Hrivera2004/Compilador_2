grammar MicroC;

// ---------------------------------------------------------------
// Gramática tal como viene en el enunciado (esMicroC).
// Cambios solo de notación ANTLR: comillas simples, '_' en vez de '-',
// λ = alternativa vacía, y reglas puente hacia los tokens del lexer.
// ---------------------------------------------------------------

// Regla de arranque (agregada): obliga a leer el archivo completo
program : translation_unit EOF ;

translation_unit
    : external_declaration
    | translation_unit external_declaration
    ;

external_declaration
    : function_definition
    | declaration
    ;

function_definition
    : function_def_header function_body
    ;

function_def_header
    : return_type identifier '(' parameters_def ')'
    ;

return_type
    : type
    | 'void'
    ;

parameters_def
    : parameter_def_list
    | 'void'
    ;

parameter_def_list
    : type identifier
    | parameter_def_list ',' type identifier
    ;

function_body
    : '{' declarations statement_list '}'
    ;

declarations
    : declarations declaration
    |                               // λ
    ;

declaration
    : variable_declaration
    | function_declaration
    ;

variable_declaration
    : type identifier_list ';'
    ;

function_declaration
    : return_type identifier '(' parameters_decl ')' ';'
    ;

parameters_decl
    : parameter_decl_list
    | parameter_decl_list ',' '...'
    | 'void'
    ;

parameter_decl_list
    : parameter_decl_spec
    | parameter_decl_list ',' parameter_decl_spec
    ;

parameter_decl_spec
    : type identifier
    | 'char' '*' identifier
    ;

statement_list
    : statement_list statement
    | statement
    ;

statement
    : expression ';'
    | 'return' ';'
    | 'return' expression ';'
    | 'while' '(' expression ')' statement
    | 'if' '(' expression ')' statement
    | 'if' '(' expression ')' statement 'else' statement
    | '{' statement_list '}'
    | 'break' ';'
    | 'continue' ';'
    ;

expression
    : equality_expression '=' equality_expression
    | equality_expression
    ;

equality_expression
    : relational_expression eq_op relational_expression
    | relational_expression
    ;

relational_expression
    : simple_expression rel_op simple_expression
    | simple_expression
    ;

simple_expression
    : simple_expression add_op term
    | term
    ;

term
    : term mul_op factor
    | factor
    ;

factor
    : constant
    | identifier
    | '(' expression ')'
    | add_op factor
    | identifier '(' expression_list ')'
    | identifier '(' ')'
    ;

constant
    : string_constant
    | numeric_constant
    | char_constant
    ;

numeric_constant
    : integer_constant
    ;

expression_list
    : expression
    | expression_list ',' expression
    ;

identifier_list
    : identifier
    | identifier_list ',' identifier
    ;

type
    : 'int'
    | 'char'
    ;

// ---------------------------------------------------------------
// Reglas puente: los terminales de la tabla en minúscula
// apuntan a tokens del lexer (en MAYÚSCULA).
// ---------------------------------------------------------------
identifier       : IDENTIFIER ;
integer_constant : INTEGER_CONSTANT ;
string_constant  : STRING_CONSTANT ;
char_constant    : CHAR_CONSTANT ;

// Operadores (sección 'Operadores' del manual de Micro-C)
eq_op  : '==' | '!=' ;
rel_op : '<' | '<=' | '>' | '>=' ;
add_op : '+' | '-' ;
mul_op : '*' | '/' ;

// ---------------------------------------------------------------
// LEXER
// ---------------------------------------------------------------
// letra = '_' | a..z | A..Z ;  identificador = letra (letra | dígito)*
// (la longitud máxima de 12 se valida después, no aquí)
IDENTIFIER       : [a-zA-Z_] [a-zA-Z_0-9]* ;

// Decimal o hexadecimal estilo C (0xFB). El rango de 16 bits se valida después.
INTEGER_CONSTANT : '0' [xX] [0-9a-fA-F]+
                 | [0-9]+
                 ;

// Un solo carácter imprimible entre comillas simples, sin secuencias de escape
CHAR_CONSTANT    : '\'' ~['\r\n] '\'' ;

// Solo se permiten las secuencias \"  \n  \\ ; no puede pasar de una línea
STRING_CONSTANT  : '"' ( '\\' ["n\\] | ~["\\\r\n] )* '"' ;

// Ignorados: espacio, tab, CR, newline y comentarios /* ... */ (no anidados)
WS      : [ \t\r\n]+ -> skip ;
COMMENT : '/*' .*? '*/' -> skip ;
