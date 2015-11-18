// ASU CSE 591
// Author: Group 4

function sendAjax() {

	// get inputs
  tags = document.getElementById("tags").value;
  count = document.getElementById("count").value;
  if(count == ""){
    count = 1;
  }

	//loadText = document.getElementById("loadtext");

	var xhr = createCORSRequest('GET', "http://52.32.6.201:80/test/"+tags+"/"+count);
    if (!xhr) {
        throw new Error('CORS not supported');
    }
    else {
        xhr.send();
        //loadText.textContent = "Loading";
        $("#loading-animation").removeClass("invisible")
        xhr.onreadystatechange = function() {
        console.log(xhr.readyState + xhr.status);
        if (xhr.readyState == 4 && xhr.status == 200) {
            //loadText.textContent = "Done";
            $("#loading-animation").addClass("invisible")
            setMarkers(parseJSON(xhr.responseText));
        }
    }
    }
}

function parseJSON(json) {
    var jsonArray = JSON.parse(json);
    var markerArray = [];
    for(i=0;i<jsonArray.length;i++) {
  		var jsonObject = jsonArray[i];
  		var markerObject = new Object();
  		var latlngObject = new Object();
  		latlngObject.lat = jsonObject.lat;
  		latlngObject.lng = jsonObject.lon;
  		markerObject.latlng = latlngObject;
  		markerArray.push(markerObject);
  	}
  	return markerArray;
}

var map;
var markers=[];

function initMap() {
	    map = new google.maps.Map(document.getElementById('map'), {
		center: {lat: -34.397, lng: 150.644},
		zoom: 4
	});
  }

function setMarkers(markerArray) {
    clearMap();
    for(i=0;i<markerArray.length;i++) {
        var jsonMarker = markerArray[i];
        var marker = new google.maps.Marker({
  		map: map,
  		position: jsonMarker.latlng,
  		title: ''
  	});
  	    markers.push(marker);
    }

    var bounds = new google.maps.LatLngBounds();
  	for(i=0;i<markers.length;i++) {
  		bounds.extend(markers[i].getPosition());
  	}

  	map.fitBounds(bounds);
}

function clearMap(){
    for(i=0;i<markers.length;i++) {
  		markers[i].setMap(null);
  	}
  	markers.length = 0;
}

function createCORSRequest(method, url) {
  var xhr = new XMLHttpRequest();
  if ("withCredentials" in xhr) {

    // Check if the XMLHttpRequest object has a "withCredentials" property.
    // "withCredentials" only exists on XMLHTTPRequest2 objects.
    xhr.open(method, url, true);

  } else if (typeof XDomainRequest != "undefined") {

    // Otherwise, check if XDomainRequest.
    // XDomainRequest only exists in IE, and is IE's way of making CORS requests.
    xhr = new XDomainRequest();
    xhr.open(method, url);

  } else {

    // Otherwise, CORS is not supported by the browser.
    xhr = null;

  }
  return xhr;
}
