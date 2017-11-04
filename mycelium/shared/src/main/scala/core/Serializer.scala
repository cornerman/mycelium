package mycelium.core

//TODO: simpler serializer for our use case: need one for server- and clientmsg => less type params :)
trait Serializer[Encoder[_], Decoder[_], PickleType] {
  def serialize[T : Encoder](msg: T): PickleType
  def deserialize[T : Decoder](bm: PickleType): Either[Throwable, T]
}
