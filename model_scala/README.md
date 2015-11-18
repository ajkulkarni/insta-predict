# Geolocation Model Generator

This subproject contains the Scala code for the backend of our
geolocation prediction engine. The code contained in this project
is responsible for generating the discrete location cells that
will form our classes by clustering the input data, building Naive
Bayes models based on the data, and finally using those models
to predict locations in real time in response to incoming web service
requests.

### Requests

Requests will be of the following JSON format:
```json
{
  tags: ["tag1", "tag2" ...],
  count: 5
  param: { ... }
},
```

Thus, the request has a `tags` property, which contains the array of tags that will be used for 
prediction. The `count` property indicates that the client is requesting the top `count`
most likely locations for the tags. The request also has a `param` property, which is not used
by the prediction, but is only there in case the client needs to associated
some information with the request and have that available in the response.

### Responses 

Responses will have the following JSON format:
```json
{
  status: 200,
  error: "This is just an example.",
  param: { ... },
  results: [
    {
      center: [33.21, -112.51],
      polygon: [
        [33.21, -112.51]
      ],
      confidence: .42
    },
    {
      center: [38.2, -116.42],
      polygon: [
        [38.2, -116.42]
      ],
      confidence: .28
    }
  ]
}
```

Thus the response object has a `status` property, where 200 indicates a normal response, and
a value other than 200 indicates an error. If there is an error, there will be an `error`
property with a string message. The `param` property sent in the request will be returned.
The `results` property will contain an array of result object, where each result object
will have 3 properties: `center`, `polygon`, and `confidence`. `center` will be the center
of the area where the image is predicted to have been taken. For now, `polygon` will just
contain a single point that will be the same as the center, but it may be fully implemented
in the future. `confidence` will contain a floating point value between 0.0 and 1.0 that
indicates the relatively likelyhood that the image was taken in this location. The results
will be sorted in descending order of confidence.