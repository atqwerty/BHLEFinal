package com.FinalPrep

import java.io.{File, InputStream}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, ResponseEntity}
import akka.http.scaladsl.server.Directives._
import akka.stream.{ActorMaterializer, IOResult}
import akka.stream.scaladsl.{FileIO, Source, StreamConverters}
import akka.util.{ByteString, Timeout}
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.model.{S3Object, S3ObjectInputStream}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import akka.pattern.ask

import collection.JavaConverters._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

object Main extends App with JsonSupport {

  val log = LoggerFactory.getLogger("Main")

  val awsCreds = new BasicAWSCredentials(
    "AKIAJE676NBQTGVITARA",
    "3TNDbl/xq416VZ5Dz+DRpLtsGNQumaI/0kntM5Ap"
  )

  val bucketName = "testdenisscala"

  val s3Client: AmazonS3 = AmazonS3ClientBuilder.standard
    .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
    .withRegion(Regions.US_EAST_1)
    .build

  if(!s3Client.doesBucketExistV2(bucketName)){
    log.info("Bucket does not exists")
    s3Client.createBucket(bucketName)
  }

  implicit val system = ActorSystem()

  implicit val materializer = ActorMaterializer()

  implicit val executionContext = system.dispatcher

  implicit val timeout = Timeout(30.seconds)

  val fileWorker = system.actorOf(FileWorker.props(s3Client, bucketName), "fileworker")

  val route =
    concat(
      path("filenames"){
        get{
          complete{
            (fileWorker ? FileWorker.GetFilenamesFromS3).mapTo[FileWorker.BucketFilenames]
          }
        }
      },
      path("file"){
        get{
          parameter('filename.as[String]) { filename =>
            val futureEntity = (fileWorker ? FileWorker.GetFile(filename)).mapTo[S3ObjectInputStream]
            onSuccess(futureEntity) { inputStream =>
              val result: Source[ByteString, Future[IOResult]] = StreamConverters.fromInputStream(() => inputStream)
              complete(HttpEntity(ContentTypes.`application/octet-stream`, result))
            }
          }
        } ~
        post {
          fileUpload("filename") {
            case(metadata, fileStream) =>
              val inputStream: InputStream = fileStream.runWith(StreamConverters.asInputStream())
              complete {
                (fileWorker ? FileWorker.PutToS3(inputStream, metadata)).mapTo[Either[Error, Accepted]]
              }
          }
        } ~
        delete {
          parameter('filename.as[String]) { filename =>
            complete {
              (fileWorker ? FileWorker.DeleteFile(bucketName, filename)).mapTo[Either[Error, Accepted]]
            }
          }
        }
      }
    )

  val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
  log.info("Listening on port 8080...")
}
