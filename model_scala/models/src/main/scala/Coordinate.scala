/**
 * Coordinate.scala
 * Representations and conversions for geolocation coordinates.
 * Author: Nathan Flick
 */

package com.github.nflick.models

case class LLA(lat: Double, lon: Double, alt: Double) {
  // Latitude and longitude in degrees, altitude in meters.
  
  def toECEF : ECEF = {
    // a is radius, e is eccentricity
    // See https://gist.github.com/klucar/1536194
    val a = 6378137
    val e = 8.1819190842622e-2
    val esq = Math.pow(e, 2)

    val latRads = lat / 180.0 * Math.PI
    val lonRags = lon / 180.0 * Math.PI

    val n = a / Math.sqrt(1 - esq * Math.pow(Math.sin(latRads), 2))
    val x = (n + alt) * Math.cos(latRads) * Math.cos(lonRags)
    val y = (n + alt) * Math.cos(latRads) * Math.sin(lonRags)
    val z = ((1  - esq) * n + alt) * Math.sin(latRads)

    ECEF(x, y, z)
  }
}

case class ECEF(x: Double, y: Double, z: Double) {
  // x, y, and z in meters.

  def toLLA : LLA = {
    // a is radius, e is eccentricity
    // See https://gist.github.com/klucar/1536194
    val a = 6378137
    val e = 8.1819190842622e-2
    val asq = Math.pow(a, 2)
    val esq = Math.pow(e, 2)

    val b = Math.sqrt(asq * (1 - esq))
    val bsq = Math.pow(b, 2)
    val ep = Math.sqrt((asq - bsq) / bsq)
    val p = Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2))
    val th = Math.atan2(a*z, b*p)

    val lon = Math.atan2(y, x);
    val lat = Math.atan2((z + Math.pow(ep, 2) * b * Math.pow(Math.sin(th), 3)),
      (p - esq * a * Math.pow(Math.cos(th), 3)))
    val n = a / Math.sqrt(1 - esq * Math.pow(Math.sin(lat), 2))
    val alt = p / Math.cos(lat) - n;

    LLA(lat / Math.PI * 180.0, lon / Math.PI * 180.0, alt)
  }
}