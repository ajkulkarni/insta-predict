// ASU CSE 591
// Author: Group 4

'use strict';

(function(){
  var app = angular.module('LocationPredictor', []);

  app.config(function($interpolateProvider) {
    $interpolateProvider.startSymbol('[[');
    $interpolateProvider.endSymbol(']]');
  });

  app.controller('PredictionController', ['$http', function($http) {
    this.currentTags = '';
    this.currentCount = '';
    this.searchHistory = [];
    this.loading = false;
    this.polygons = [];
    this.markers = [];

    var ctrl = this;
    this.predict = function() {
      this.loading = true;
      var tags = ctrl.currentTags.split(/\W+/);
      if (ctrl.currentCount === '') {
        var params = {};
      } else {
        var params = { count: parseInt(ctrl.currentCount) };
      }

      var url = '/api/predict/' + tags.join('+');
      $http.get(url, { params: params}).then(function success(response) {
        ctrl.setMap(response.data.result);
        ctrl.addHistory(tags, response.data.result);
        ctrl.loading = false;
      }, function error(response) {
        ctrl.loading = false;
        alert('Request for ' + url + ' returned status ' + response.status + '.');
      })
    };

    this.setMap = function(predictions) {
      for (var i = 0; i < ctrl.polygons.length; i++) {
        ctrl.polygons[i].setMap(null);
      }
      ctrl.polygons = [];

      for (var i = 0; i < ctrl.markers.length; i++) {
        ctrl.markers[i].setMap(null);
      }
      ctrl.markers = [];

      var bounds = new google.maps.LatLngBounds();
      for (var i = 0; i < predictions.length; i++) {
        var prediction = predictions[i];
        var polygon = prediction.polygon;
        var color = ctrl.gradient($.Color("#5c00e6"), $.Color("#fc0000"), prediction.probability);

        var path = []
        for (var j = 0; j < polygon.length; j++) {
          path.push({ lat: polygon[j][0], lng: polygon[j][1] });
        }

        var mapPoly = new google.maps.Polygon({
          paths: path,
          strokeColor: color,
          strokeOpacity: 1.0,
          strokeWeight: 2,
          fillColor: color,
          fillOpacity: 0.35
        });

        mapPoly.setMap(window.map);
        ctrl.polygons.push(mapPoly);

        var paths = mapPoly.getPaths();
        for (var j = 0; j < paths.getLength(); j++) {
          var path = paths.getAt(j);
          for (var k = 0; k < path.getLength(); k++) {
            bounds.extend(path.getAt(k));
          }
        }

        var icon = new google.maps.MarkerImage("http://chart.apis.google.com/chart?chst=d_map_pin_letter&chld=%E2%80%A2|" + color.substring(1),
          new google.maps.Size(21, 34),
          new google.maps.Point(0,0),
          new google.maps.Point(10, 34));
        var marker = new google.maps.Marker({
          icon: icon,
          position: { lat: prediction.center[0], lng: prediction.center[1] },
          map: window.map
        });
        ctrl.markers.push(marker);

        bounds.extend(marker.getPosition());
      }

      window.map.fitBounds(bounds);
    };

    this.addHistory = function(tags, predictions) {
      ctrl.searchHistory.push({
        tags: tags,
        predictions: predictions
      });
    };

    this.gradient = function(start, end, value) {
      var sat = value * (end.saturation() - start.saturation()) + start.saturation();
      var lightness = value * (end.lightness() - start.lightness()) + start.lightness();

      var delta = end.hue() - start.hue();
      if (delta > 180.0) {
        delta = -((360.0 - delta) % 360.0);
      } else if (delta < -180.0) {
        delta = (360.0 + delta) % 360.0;
      }
      var hue = (value * delta + start.hue() + 360.0) % 360.0;
      return $.Color({ hue: hue, saturation: sat, lightness: lightness}).toHexString();
    }

  }]);
})();

function initMap() {
  window.map = new google.maps.Map(document.getElementById('map'), {
    center: { lat: 20.0, lng: -30.0 },
    zoom: 3
  });
}
