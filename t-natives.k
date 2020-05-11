//!father(John, Tom);
!daughter(Sarah, John);
!@x @y ($z parent(z,x) && parent(z,y)) -> (~child(x,y) && ~child(y,x));
!@x $y parent(y,x);
!@x @y daughter(x,y) -> female(x), child(x,y);
//!@x @y father(x,y) -> male(x), parent(x,y);

