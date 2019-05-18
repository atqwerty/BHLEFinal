package com.FinalPrep

import java.io.InputStream

import akka.actor.{Actor, ActorLogging, Props}
import akka.http.scaladsl.server.directives.FileInfo
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.{ObjectMetadata, PutObjectRequest, S3ObjectInputStream}

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

object FileWorker {
  case class PutToS3(inputstream: InputStream, metadata: FileInfo)
  case object GetFilenamesFromS3
  case class GetFile(key: String)
  case class BucketFilenames(filenames: List[String])
  case class DeleteFile(bucketName: String, key: String)

  def props(s3Client: AmazonS3, bucketName: String) = Props(new FileWorker(s3Client, bucketName))
}

class FileWorker(s3Client: AmazonS3, bucketName: String) extends Actor with ActorLogging {
  import FileWorker._



  override def receive: Receive = {
    case PutToS3(inputstream, fileInfo) =>
      val key = fileInfo.getFileName
      log.info(s"Putting key $key to S3")

      val objectMetadata = new ObjectMetadata()
      objectMetadata.setContentType(fileInfo.getContentType.toString)
      val putObjectRequest = new PutObjectRequest(bucketName, key, inputstream, objectMetadata)

      Try(s3Client.putObject(putObjectRequest)) match {
        case Success(result) =>
          log.info(s"Successfully put the key: $key to S3")
          sender() ! Right(Accepted(200, s"Successfully put the key: $key"))

        case Failure(exception) =>
          log.error(exception, "Error when putting to S3: ")
          sender() ! Left(Error(500, exception.getMessage))
      }

    case GetFilenamesFromS3 =>
      log.info("Recieved GetFilenamesFromS3")
      val filenames = s3Client.listObjects(bucketName).getObjectSummaries.asScala.toList.map(_.getKey)
      sender() ! BucketFilenames(filenames)

    case GetFile(key) =>
      val s3Object = s3Client.getObject(bucketName, key)
      val dataStream: S3ObjectInputStream = s3Object.getObjectContent

      sender() ! dataStream

    case DeleteFile(bucketName, key) =>
      Try(s3Client.deleteObject(bucketName, key)) match {
        case Success(result) =>
          sender() ! Right(Accepted(200, "Good"))

        case Failure(exception) =>
          log.error(exception, "Error when deleting from S3: ")
          sender() ! Left(Error(500, exception.getMessage))
      }
//      log.info("Recieved DeleteFile")
//      val s3Object = s3Client.deleteObject(bucketName, key)
//      val dataStream: S3ObjectInputStream = s3Object.getObjectContent
//      sender() ! s3Object
  }
}
