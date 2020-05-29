# Link service
This service simulates a microservice that maintains the links between users in a social network.  The APIs are:

## GET /

### Response:
* Status code 200
* Headers: []
* Supported content type:
    * `application/json`
* Response body example
```javascript
"Welcome to REST servce in functional Scala!"
```

## GET /version_info

### Response:
* Status code 200
* Headers: []
* Supported content type:
    * `application/json`
* Response body example
```javascript
{
    "name": "service-in-fp-scala",
    "version": "0.1",
    "scalaVersion": "2.12.7",
    "sbtVersion": "1.3.10",
    "gitCommitHash": "0467ccd46df05969aa27e3de75f56d17a525ccbc",
    "gitCommitMessage": "Simplify `WebApiSpec`\n\nBuild docker image\n",
    "gitCommitDate": "Tue Dec 19 00:43:30 2019 +1100",
    "gitCurrentBranch": "master"
}
```

## POST /users/:userId/links
For a user to add a link with another user
### Captures:
* `userId` - the user id (initiator) who wants to add a link with another user
### POST parameters:
* `targetId` - the user id to be linked by the initiator
### Request:
* Supported content type:
    * `application/json`
* Body example:
```javascript
"bert"
```
### Response:
* Status code 200
* Headers: []
* Supported content type:
    * `application/json`
* Response body example
```javascript
"cedae736-d8ba-464f-9ff9-a173b016572a"
```

## GET /users/:userId/links?status=:linkStatus&is_initiator=:isInitiator
Find the links associated with the user according to the query parameters if there is any.
### Captures:
* `UserId` - the user id of which the links to be queried
### Optional query parameters:
* `linkStatus` - link status.  Possible values: [`Pending`|`Accepted`]
* `isInitiator` - whether the user id is the link initiator.  Possible values: [`true`|`false`]
### Response:
* Status code 200
* Headers: []
* Supported content type:
    * `application/json`
* Response body example
```javascript
[
    "f14b8dd6-1f7c-4b31-a1e4-0dd589cdd502",
    "5e73f52c-fd08-47bc-9a87-126bfe1d8e3c"
]
```

## PUT /links/:linkId
Accept the link
### Captures:
* `linkId`
### Response:
* Status code 200
* Headers: []
* Supported content type:
    * `application/json`
* Response body example
```javascript
"LinkId cedae736-d8ba-464f-9ff9-a173b016572a accepted"
```

## DELETE /links/:linkId
Delete the link
### Captures:
* `linkId`
### Response:
* Status code 200
* Headers: []
* Supported content type:
    * `application/json`
* Response body example
```javascript
"Linkid cedae736-d8ba-464f-9ff9-a173b016572a removed successfully"
```
or
```javascript
"No need to remove non-exist linkid cedae736-d8ba-464f-9ff9-a173b016572a"
```

## GET /links/:linkId
Get the link details
### Captures:
* `linkId`
* Headers: [(`Authorization`, `Bearer theUserId`)]
### Response:
* Status code 401 if authorization token is not attached, o.w. Status code 200
* Headers: []
* Supported content type:
    * `application/json`
* Response body example
```javascript
[
    {
        "id": "f14b8dd6-1f7c-4b31-a1e4-0dd589cdd502",
        "initiatorId": "mikasa",
        "targetId": "eren",
        "status": "Accepted",
        "creationDate": "2020-05-29T01:05:52.525Z",
        "confirmDate": "2020-05-29T01:05:52.668Z",
        "uniqueKey": "eren_mikasa"
    }
]
```

## Note
The aim of attaching authorization information to the "get the link details" request is to illustrate how easy it is to set authorization requirement on a request by using the http4s middleware.