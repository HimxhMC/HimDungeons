package com.him.dungeons.util;

public final class Pos3 {
    public final int x, y, z;
    public Pos3(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
    public static Pos3 at(int x, int y, int z) { return new Pos3(x, y, z); }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public Pos3 add(Pos3 o) { return new Pos3(x + o.x, y + o.y, z + o.z); }
    public Pos3 add(int dx, int dy, int dz) { return new Pos3(x + dx, y + dy, z + dz); }
    public Pos3 subtract(Pos3 o) { return new Pos3(x - o.x, y - o.y, z - o.z); }
    public Pos3 subtract(int dx, int dy, int dz) { return new Pos3(x - dx, y - dy, z - dz); }
    @Override public boolean equals(Object o) {
        if (!(o instanceof Pos3 p)) return false;
        return x == p.x && y == p.y && z == p.z;
    }
    @Override public int hashCode() { return (x * 31 + y) * 31 + z; }
    @Override public String toString() { return "(" + x + ", " + y + ", " + z + ")"; }
    /** 用于兼容 BlockVector3.at(double,double,double) 的调用 */
public static Pos3 at(double x, double y, double z) {
    return new Pos3((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
}

public Pos3 add(double dx, double dy, double dz) {
    return new Pos3((int) Math.floor(x + dx), (int) Math.floor(y + dy), (int) Math.floor(z + dz));
}
}