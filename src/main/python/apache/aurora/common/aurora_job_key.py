import re

from twitter.common.lang import Compatibility, total_ordering

from gen.apache.aurora.constants import GOOD_IDENTIFIER_PATTERN_PYTHON
from gen.apache.aurora.ttypes import Identity, JobKey, TaskQuery

# TODO(ksweeney): This can just probably just extend namedtuple.
@total_ordering
class AuroraJobKey(object):
  """A canonical representation of a key that can identify a job in any of the clusters the client
  is aware of."""
  class Error(Exception): pass
  class TypeError(TypeError, Error): pass
  class InvalidIdentifier(ValueError, Error): pass
  class ParseError(ValueError, Error): pass

  VALID_IDENTIFIER = re.compile(GOOD_IDENTIFIER_PATTERN_PYTHON)

  def __init__(self, cluster, role, env, name):
    if not isinstance(cluster, Compatibility.string):
      raise self.TypeError("cluster should be a string, got %s" % (cluster.__class__.__name__))
    self._cluster = cluster
    self._role = self._assert_valid_identifier("role", role)
    self._env = self._assert_valid_identifier("env", env)
    self._name = self._assert_valid_identifier("name", name)

  @classmethod
  def from_path(cls, path):
    try:
      cluster, role, env, name = path.split('/', 4)
    except ValueError:
      raise cls.ParseError(
          "Invalid path '%s'. path should be a string in the form CLUSTER/ROLE/ENV/NAME" % path)
    return cls(cluster, role, env, name)

  @classmethod
  def from_thrift(cls, cluster, job_key):
    if not isinstance(job_key, JobKey):
      raise cls.TypeError("job_key must be a Thrift JobKey struct")
    return cls(cluster, job_key.role, job_key.environment, job_key.name)

  @classmethod
  def _assert_valid_identifier(cls, field, identifier):
    if not isinstance(identifier, Compatibility.string):
      raise cls.TypeError("%s must be a string" % field)
    if not cls.VALID_IDENTIFIER.match(identifier):
      raise cls.InvalidIdentifier("Invalid %s '%s'" % (field, identifier))
    return identifier

  @property
  def cluster(self):
    return self._cluster

  @property
  def role(self):
    return self._role

  @property
  def env(self):
    return self._env

  @property
  def name(self):
    return self._name

  def to_path(self):
    return "%s/%s/%s/%s" % (self.cluster, self.role, self.env, self.name)

  def to_thrift(self):
    return JobKey(role=self.role, environment=self.env, name=self.name)

  def to_thrift_query(self):
    return TaskQuery(owner=Identity(role=self.role), environment=self.env, jobName=self.name)

  def __iter__(self):
    """Support 'cluster, role, env, name = job_key' assignment."""
    return iter((self._cluster, self._role, self._env, self._name))

  def __repr__(self):
    return "%s(%r, %r, %r, %r)" % (self.__class__, self._cluster, self._role, self._env, self._name)

  def __str__(self):
    return self.to_path()

  def __hash__(self):
    return hash(AuroraJobKey) + hash(self.to_path())

  def __eq__(self, other):
    if not isinstance(other, AuroraJobKey):
      return NotImplemented
    return self.to_path() == other.to_path()

  def __lt__(self, other):
    if not isinstance(other, AuroraJobKey):
      return NotImplemented
    return self.to_path() < other.to_path()
