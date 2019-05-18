package com.FinalPrep

trait Response {
  def status: Int
  def message: String
}

case class Accepted(status: Int = 200, message: String) extends Response

// for now respond with 200, but in Error we save the status
case class Error(status: Int = 500, message: String) extends Response