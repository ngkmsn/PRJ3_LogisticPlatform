import { useQueryClient } from '@tanstack/react-query'
import { router } from 'expo-router'
import { useState } from 'react'
import { createOrder } from '../../api/endpoints'
import { Button, ErrorText, Field, Screen } from '../../components/ui'
import { validateOrderForm, type OrderFormFields } from '../../features/rules'

const EMPTY: OrderFormFields = {
  recipientName: '',
  recipientPhone: '',
  deliveryAddress: '',
  notes: '',
  productName: '',
  quantity: '1',
  unitPrice: '',
}

export default function NewOrder() {
  const queryClient = useQueryClient()
  const [form, setForm] = useState<OrderFormFields>(EMPTY)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const set = (key: keyof OrderFormFields) => (value: string) => setForm((f) => ({ ...f, [key]: value }))

  async function onSubmit() {
    const result = validateOrderForm(form)
    if (!result.ok) {
      setError(result.error)
      return
    }
    setError(null)
    setSubmitting(true)
    try {
      const order = await createOrder(result.body)
      await queryClient.invalidateQueries({ queryKey: ['my-orders'] })
      router.replace(`/customer/order/${order.id}`)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not create the order')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Screen>
      <Field label="Recipient name" value={form.recipientName} onChangeText={set('recipientName')} />
      <Field label="Recipient phone" value={form.recipientPhone} onChangeText={set('recipientPhone')} keyboardType="phone-pad" />
      <Field label="Delivery address" value={form.deliveryAddress} onChangeText={set('deliveryAddress')} multiline />
      <Field label="Product" value={form.productName} onChangeText={set('productName')} />
      <Field label="Quantity" value={form.quantity} onChangeText={set('quantity')} keyboardType="number-pad" />
      <Field label="Unit price (₫)" value={form.unitPrice} onChangeText={set('unitPrice')} keyboardType="number-pad" />
      <Field label="Notes (optional)" value={form.notes} onChangeText={set('notes')} />
      <ErrorText>{error}</ErrorText>
      <Button title="Create delivery" onPress={onSubmit} loading={submitting} />
    </Screen>
  )
}
