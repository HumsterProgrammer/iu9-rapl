readnum CALL 1 ADD printnum CALL
0 HALT

:printnum  ; ... x ra -> ...
OVER       ; ... x ra x
0 CMP printnum_onzero JEQ
OVER printnum_rec CALL RET2
:printnum_onzero
48 OUT RET2

:printnum_rec ; ... x ra -> ...
SWAP ; ... ra x
DUP  ; ... ra x x
printnum_rec_onzero JEQ
DUP 10 MOD ; ra x x%10
SWAP ; ... ra x%10 x
10 DIV ; ... ra x%10 x/10
printnum_rec CALL ; ra x%10
48 ADD OUT RET
:printnum_rec_onzero ; ... ra 0
DROP RET

:g_cur_char -1

:getchar ; ... rv -> ... char
g_cur_char LOAD DUP ; ... rv [g_cur_char] [g_cur_char]
getchar_empty JLT
; опустошаем буфер
; ... rv [g_cur_char]  | [g_cur_char] >= 0
1 NEG g_cur_char SAVE
SWAP RET
:getchar_empty ; ... rv -1
DROP ; ... rv
IN   ; ... rv char
SWAP ; ... char rv
RET

:peekchar ; ... rv -> ... char
getchar CALL DUP g_cur_char SAVE SWAP RET

:readnum ; ... rv -> ... value
0 ; ... rv 0=accum
:readnum_loop ; ... rv accum
peekchar CALL ; ... rv accum char
48 CMP readnum_exit JLT
peekchar CALL
57 CMP readnum_exit JGT
getchar CALL 48 SUB ; ... rv accum digit
SWAP 10 MUL ADD
readnum_loop JMP
:readnum_exit ; ... rv accum
SWAP RET