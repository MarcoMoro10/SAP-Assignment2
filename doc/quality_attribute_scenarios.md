# Quality Attribute Scenarios

**Quality attribute scenario**: Responsiveness

**Feature**: Small average response time in case of overload

_when_ initiate 1000 concurrent requests \
_caused by_ 1000 users \
_occur in_ the system \
_operating in_ normal operation \
_then_ the system processes all requests \
_so that_ the average response time is < 100 milliseconds


**Quality attribute scenario**: Handling partial failures

**Feature**: Avoid pointless requests in case of unavailability of account service

_when_ more than 50% of total requests fail \
_caused by_ unavailability of account service \
_occur in_ the system \
_operating in_ normal operation \
_then_ the system makes the requests fail immediately without propagating to the account service \
_so that_ it lightens the workload avoiding pointless requests
