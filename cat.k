!@x @y eating(x, y) -> (sees(x, y), catches(x, y)) || (~sees(x, y), ~catches(x, y));
!@x @y (hides(x), ~sees(y, x)) || (~hides(x), sees(y, x));
!@x hides(x) || ~hides(x);
!eating(Cat, Mouse);
