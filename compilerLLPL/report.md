% Лабораторная работа № 2 «Основы программирования на Рефале»
% 1 апреля 2025 г.
% Денис Кочетков, ИУ9-61Б

# Цель работы

Целью данной работы является ознакомление с языком программирования Рефал-5
 и библиотекой LibraryEx из refal-5-framework.

# Индивидуальный вариант

## UNIX-утилита

Программа head, распечатывающая первые несколько строк каждого файла.

```
head [-‹число›] ‹имена файлов›…
```

Если количество строк не указано, подразумевается 10.

При реализации программы на Рефале-5 ключ командной строки должен начинаться на знак «+»,
 т.к. Arg аргументы, начинающиеся на «-», игнорирует.

## Символьная функция

Регулярное выражение описано следующим абстрактным синтаксисом:

RegEx → ∅ | ε | SYMBOL | RegEx ⋃ RegEx | RegEx · RegEx | RegEx*

Здесь ∅ — пустое множество, ⋃ — объединение, · — конкатенация.

Требуется написать функцию <Simplify t.RegEx> == t.RegEx, упрощающую регулярное выражение:

- r∅ = ∅r = ∅
-  r ∪ ∅ = ∅ ∪ r = r
-  rε = εr = r
-  ε* = ∅* = ε


# Реализация UNIX-утилиты

head.ref

```
*$FROM LibraryEx
$EXTERN ArgList, Map, LoadFile;

$ENTRY Go {
	= <Main <ArgList>>
}

Main{
	(e.ProgName) ('+' e.Count) (e.OneFile) = <HeadFile (e.Count) NoHeader (e.OneFile)>;
	(e.ProgName) ('+' e.Count) e.FileList = <Map (HeadFile (e.Count) WithHeader) e.FileList>;
	(e.ProgName) ('+' e.Count) /*empty*/ = <Prout <HeadSTDIO e.Count >>;
	
	(e.ProgName) /*Пусто*/ (e.OneFile) = <HeadFile ('10') NoHeader (e.OneFile)>;
	(e.ProgName) /*Пусто*/ e.FileList = <Map (HeadFile ('10') WithHeader) e.FileList>;
	(e.ProgName) /* empty */ = <Prout <HeadSTDIO 10 >>;
}

WithHeader {
    e.FileName = <Prout '==>' e.FileName '<=='>;
}

NoHeader {
    e.FileName = /* do nothing */;
}

$ENTRY HeadFile {
	(e.Count) s.Header (e.FileName)
	    = <Mu s.Header e.FileName> <Prout <HeadText (<Numb e.Count>) <Lines <LoadFile e.FileName>>>>
}
HeadSTDIO{
	0 = /*empty*/;
	1 = <Card>;
	e.Count = <Card> '\n' <HeadSTDIO <Sub e.Count 1>> 
}

Lines {
	e.Line '\n' e.Tail = (e.Line) <Lines e.Tail>;
	e.NoCat = (e.NoCat);
	/* Пусто */ = /* Пусто */
}

HeadText{
	(0) (e.Val) = /* Пусто */;
	(1) ((e.Line) e.Tail) =  e.Line;
	(e.Count) ((e.Line)) = e.Line;
	(e.Count) ((e.Line) e.Tail) = e.Line '\n' <HeadText (<Sub e.Count 1>) (e.Tail)>;
	(e.Count) () =  /* Пусто */;
}
```

# Тестирование UNIX-утилиты

```
vudrav@vudrav-XL442:~/Рабочий стол/rayp2$ ./interpret head +3 *.ref
==>head.ref<==
*$FROM LibraryEx
$EXTERN ArgList, Map, LoadFile;

==>regex.ref<==
*$FROM LibraryEx
$EXTERN ArgList, Map, LoadExpr;

==>var11.ref<==
$ENTRY Go{
	/* empty */ = <Prout <Parse 'abbbabbbaa'>>
}
vudrav@vudrav-XL442:~/Рабочий стол/rayp2$ ./interpret head head.ref
*$FROM LibraryEx
$EXTERN ArgList, Map, LoadFile;

$ENTRY Go {
	= <Main <ArgList>>
}

Main{
	(e.ProgName) ('+' e.Count) (e.OneFile) = <HeadFile (e.Count) NoHeader (e.OneFile)>;
	(e.ProgName) ('+' e.Count) e.FileList = <Map (HeadFile (e.Count) WithHeader) e.FileList>;
```

# Реализация символьных преобразований

regex.ref

```
*$FROM LibraryEx
$EXTERN ArgList, Map, LoadExpr;

/*
Регулярное выражение описано следующим абстрактным синтаксисом:
RegEx → ∅ | ε | SYMBOL | RegEx ⋃ RegEx | RegEx · RegEx | RegEx*
Здесь ∅ — пустое множество, ⋃ — объединение, · — конкатенация.
Требуется написать функцию <Simplify t.RegEx> == t.RegEx, упрощающую регулярное выражение:
    r∅ = ∅r = ∅
    r ∪ ∅ = ∅ ∪ r = r
    rε = εr = r
    ε* = ∅* = ε
*/

$ENTRY Go{
	= <Main <ArgList>>
}

Main{
	(e.ProgName) /*empty*/  = <RegexProcess ('regextest')>;
	(e.ProgName) e.FileList = <Map RegexProcess e.FileList>;
}

$ENTRY RegexProcess{
	(e.FileName) = <Prout <Simplify <LoadExpr e.FileName>>>
}
/*
t.RegEx ::= EmptySet
	| EmptyWord
	| s.SYMBOL 
	| (t.RegEx Union t.RegEx)
	| (t.RegEx Concat t.RegEx)
	| (t.RegEx Star)
	
<Simplify t.RegEx> == t.RegEx
*/

Simplify{
	(t.A Union t.B),
		(<Simplify t.A>) (<Simplify t.B>) : {
			(t.RegEx) (EmptySet) = t.RegEx;
			(EmptySet) (t.RegEx) = t.RegEx;
			(t.RegA) (t.RegB) = (t.RegA Union t.RegB);
		};
	(t.A Concat t.B),
		(<Simplify t.A>) (<Simplify t.B>) : {
			(t.RegEx) (EmptySet) = EmptySet;
			(EmptySet) (t.RegEx) = EmptySet;
			(t.RegEx) (EmptyWord) = t.RegEx;
			(EmptyWord)(t.RegEx) = t.RegEx;
			(t.RegA) (t.RegB) = (t.RegA Concat t.RegB);
		};
	(t.RegEx Star),
		(<Simplify t.RegEx>) : {
			(EmptySet) = (EmptyWord);
			(EmptyWord) = (EmptyWord);
			(t.NewRegEx) = (t.NewRegEx Star)
		}
	t.Other = t.Other;
}
```

# Тестирование символьных преобразований

```
vudrav@vudrav-XL442:~/Рабочий стол/rayp2$ cat regextest
((EmptySet Concat X) Union (A Concat (B Star)))
vudrav@vudrav-XL442:~/Рабочий стол/rayp2$ ./interpret regex regextest
(A Concat (B Star ))
```

# Вывод

В ходе выполнения лабораторной работы, я научился основам программирования на Рефал-5, 
освоил основы работы с образцами Рефала, файлами, и определенными пользователем структурами.
Рефал крайне удобный для переписывания строк по определенным правилам, так как может крайне гибко
искать шаблоны в строке, анализировать для них дополнительные условия, и гибко их переписывать.
