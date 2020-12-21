/**
 * Базовые правила для родственных отношений
 */

// У всех есть родители
!@x $y parent(y,x);

// Никто не может быть собственным родителем
!@x ~parent(x,x);

// Каждый является или мужчиной, или женщиной.
!@x (male(x) || female(x)) && ~(male(x) && female(x));

/* Если x дочь y то
 * 1) x женского пола,
 * 2) x является ребенком y
 */
!@x @y daughter(x,y) -> female(x), child(x,y);

/* Если x сын y, то
 * 1) x мужского пола,
 * 2) x является ребенком y
 */
!@x @y son(x,y) -> male(x), child(x,y);

/* Если x отец y, то
 * 1) x мужского пола,
 * 2) x является родителем y.
 */
!@x @y father(x,y) -> male(x), parent(x,y);

/* Если x мать y, то
 * 1) x женского пола,
 * 2) x является родителем y.
 */
!@x @y mother(x,y) -> female(x), parent(x,y);

/* Если x ребенок y, то
 * 1) у является родителем x,
 * 2) если x мужского пола, то он сын x,
 * 3) если женского – то дочь.
 */
!@x @y child(x,y) -> parent(y,x), (male(x) -> son(x,y)), (female(x) -> daughter(x,y));

/* Если x родитель y, то
 * 1) y является ребенком x,
 * 2) если x мужского пола то он отец x,
 * 3) если женского то мать
 */
!@x @y parent(x,y) -> child(y,x), (male(x) -> father(x,y)), (female(x) -> mother(x,y));

// Если двое имеют общего предка, то
// эти двое родные.
!@x @y ($z parent(z,x) && parent(z,y)) && x != y -> sibling(x,y);

// Если двое имеют общего предка, то
// эти двое не могут быть предками друг другу.
!@x @y ~(sibling(x,y), parent(x,y));

// Никто не может быть предком своего предка.
!@x @y ~(parent(x,y), parent(y,x));

// Родные симметричны
!@x @y sibling(x,y) -> sibling(y,x);

// Определение родственников
!@x @y sibling(x,y) || parent(x,y) -> native(x,y);

// Родственники симметричны
!@x @y native(x,y) -> native(y,x);

!@x @y ($z parent(x,z), parent(y,z)), x != y -> spouse(x,y) || divorced(x,y);
//!@x ~spouse(x,x);
//!@x @y spouse(x,y) -> spouse(y,x);
//!@x @y divorced(x,y) -> divorced(y,x);


/**
 * База данных
 */

// Утверждения

!father(John, Tom);
!daughter(Sarah, John);
!mother(Mary,Sarah);
!child(Tom,Mary);

// Возраст

!age(John, 37);
!age(Tom, 12);
!age(Sarah, 4);

/**
 * На этом всё
 */