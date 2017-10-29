package mycelium.core

trait Serializer[Encoder[_], Decoder[_], PickleType] {
  def serialize[T : Encoder](msg: T): PickleType
  def deserialize[T : Decoder](bm: PickleType): Either[Throwable, T]
}
