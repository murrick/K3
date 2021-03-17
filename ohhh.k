!@x (male(x) || female(x)) && ~(male(x) && female(x));
!@x @y father(x,y) -> male(x), parent(x,y);
!@x @y mother(x,y) -> female(x), parent(x,y);
!father(John, Tom);
