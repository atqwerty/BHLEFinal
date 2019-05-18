package com.FinalPrep

import spray.json.RootJsonFormat
import spray.json.DefaultJsonProtocol._

trait JsonSupport {
  implicit val acceptedFormat = jsonFormat2(Accepted)
  implicit val errorFormat = jsonFormat2(Error)
  implicit val bucketFilenamesFormat = jsonFormat1(FileWorker.BucketFilenames)
}
