select x.id, nid, zname from x inner join (y, z) on (x.id=y.id and y.id=z.nid);
