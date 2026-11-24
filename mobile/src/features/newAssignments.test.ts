import { detectNewAssignments } from './newAssignments'

const order = (orderId: string) => ({ orderId })

describe('detectNewAssignments', () => {
  it('stays quiet on the first load so existing orders do not alert', () => {
    const result = detectNewAssignments(null, [order('a'), order('b')])
    expect(result.fresh).toEqual([])
    expect([...result.seen]).toEqual(['a', 'b'])
  })

  it('reports orders that were not there before', () => {
    const result = detectNewAssignments(new Set(['a']), [order('a'), order('c')])
    expect(result.fresh).toEqual([order('c')])
    expect([...result.seen].sort()).toEqual(['a', 'c'])
  })

  it('does not alert again for an order that left and came back', () => {
    const first = detectNewAssignments(new Set(['a']), [])
    const again = detectNewAssignments(first.seen, [order('a')])
    expect(again.fresh).toEqual([])
  })
})
