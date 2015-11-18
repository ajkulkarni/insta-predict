// ASU CSE 591
// Author: Group 4

var pinColors=["ff0000","ffce00","23bd00","166903","0051ab"];
var successQueries=[];
var latestTags;
function sendAjax() {

  // get inputs
  latestTags = document.getElementById("tags").value;
  count = document.getElementById("count").value;
  if (count ==""){
    count = 1;
  }


  //loadText = document.getElementById("loadtext");
  var xhr = createCORSRequest('GET', "http://52.32.6.201:80/test/"+latestTags+"/"+count);
    if (!xhr) {
        throw new Error('CORS not supported');
    }
    else {
        xhr.send();
        //loadText.textContent = "Loading";
        $("#loading-animation").removeClass("invisible");
        xhr.onreadystatechange = function() {
        console.log(xhr.readyState + xhr.status);
        if (xhr.readyState == 4 && xhr.status == 200) {
            //loadText.textContent = "Done";
            $("#loading-animation").addClass("invisible");
            var tagList = latestTags.split(",");
            var jsonMarkerArray = parseJSON(xhr.responseText);
            var successQuery = new Object();
            successQuery.tags = tagList;
            successQuery.jsonMarkers = jsonMarkerArray;
            successQueries.splice(0,0,successQuery);
            addSuccessQueryToHistory(successQuery);
            setMarkers(jsonMarkerArray);
            fillChart(jsonMarkerArray)
        }
    }
    }
}

function addSuccessQueryToHistory(query) {
    var tagList = query.tags;
    var tableRef = document.getElementById('added-articles');
    // Insert a row in the table at the last row
    var newRow   = tableRef.insertRow(1);
    var newCell  = newRow.insertCell(0);
    for(i=0;i<tagList.length;i++) {
        // Append a text node to the cell
        var span = document.createElement('span');
        span.className = "label label-primary";
    span.textContent = tagList[i];
    span.style.padding = 4;
    span.style.margin = 4;
        newCell.appendChild(span);
    }
    tableRef.addEventListener("mouseover", function(e){
        var cell = e.target || window.event.srcElement;
        if ( cell.cellIndex >= 0 ){
            parentRow = cell.parentNode;
            console.log("Cell"+parentRow.rowIndex);
            if(parentRow.rowIndex>0)
                parentRow.style.backgroundColor = "#fdfdca"
        }
    });
    tableRef.addEventListener("mouseout", function(e){
        var cell = e.target || window.event.srcElement;
        if ( cell.cellIndex >= 0 ){
            parentRow = cell.parentNode;
            console.log("Cell"+parentRow.rowIndex);
            if(parentRow.rowIndex>0)
                parentRow.style.backgroundColor = "#ffffff"
        }
    });
    tableRef.addEventListener("click", function(e){
        var cell = e.target || window.event.srcElement;
        if ( cell.cellIndex >= 0 ){
            parentRow = cell.parentNode;
            console.log("Cell"+parentRow.rowIndex);
            if(parentRow.rowIndex>0)
               setMarkers(successQueries[parentRow.rowIndex-1].jsonMarkers);
        }
    });

}

function parseJSON(json) {
    var jsonArray = JSON.parse(json);
    var markerArray = [];
    for(i=0;i<jsonArray.length;i++) {
      var jsonObject = jsonArray[i];
      var markerObject = new Object();
      var latlngObject = new Object();
      var confidenceObject = new Object();
      latlngObject.lat = jsonObject.lat;
      latlngObject.lng = jsonObject.lon;
      markerObject.latlng = latlngObject;
      markerObject.confidence = jsonObject.confidence;
      if(i<pinColors.length){
          markerObject.icon = createPinIconFromColor(pinColors[i]);
      } else {
          markerObject.icon = createPinIconFromColor(pinColors[pinColors.length-1]);
      }
      markerArray.push(markerObject);
    }
    return markerArray;
}

function createPinIconFromColor(pinColor) {
    var pinImage = new google.maps.MarkerImage("http://chart.apis.google.com/chart?chst=d_map_pin_letter&chld=%E2%80%A2|" + pinColor,
        new google.maps.Size(21, 34),
        new google.maps.Point(0,0),
        new google.maps.Point(10, 34));
    return pinImage;
}

var map;
var markers=[];

function initMap() {
      map = new google.maps.Map(document.getElementById('map'), {
    center: {lat: -34.397, lng: 150.644},
    zoom: 8
  });
  }

function setMarkers(markerArray) {
    clearMap();
    for(i=0;i<markerArray.length;i++) {
        var jsonMarker = markerArray[i];
        var marker = new google.maps.Marker({
      map: map,
      position: jsonMarker.latlng,
      icon: jsonMarker.icon,
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

function fillChart(probabilityArray){
  var confidences = [];
  for(ii=0;ii<probabilityArray.length;ii++){
    tempVal = probabilityArray[ii].confidence.toPrecision(5);
    confidences.push(tempVal);
    //populate chart here
  }

  var x = d3.scale.linear()
    .domain([0, 1])
    .range([0, 420]);

  var graph = d3.select(".graph").selectAll("div")
      .data(confidences);

  graph.exit().remove();

  graph.enter().append("div")
      .style("width", function(d) {return x(d)+"px";})
      .text(function(d) {return d;});

  graph
    .style("width", function(d) {return x(d)+"px";})
    .text(function(d) {return d;});
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
